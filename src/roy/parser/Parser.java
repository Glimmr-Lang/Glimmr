package roy.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import roy.ast.AnnotatedFunction;
import roy.ast.AnyPattern;
import roy.ast.Arg;
import roy.ast.Ast;
import roy.ast.BinOp;
import roy.ast.BindPattern;
import roy.ast.Block;
import roy.ast.BooleanValue;
import roy.ast.Call;
import roy.ast.ConsPattern;
import roy.ast.ExpressionPattern;
import roy.ast.FieldAccess;
import roy.ast.Identifier;
import roy.ast.IfElse;
import roy.ast.JSCode;
import roy.ast.Let;
import roy.ast.LetIn;
import roy.ast.ListPattern;
import roy.ast.Match;
import roy.ast.MatchCase;
import roy.ast.ObjectPattern;
import roy.ast.Pattern;
import roy.ast.RClosure;
import roy.ast.RFunction;
import roy.ast.RObject;
import roy.ast.RString;
import roy.ast.Tuple;
import roy.ast.TuplePattern;
import roy.ast.TypeAlias;
import roy.ast.Unit;
import roy.errors.ErrorNode;
import roy.errors.Errors;
import roy.fs.Fs;
import roy.rt.It;
import roy.tokens.Token;
import roy.tokens.TokenKind;
import roy.types.AppType;
import roy.types.FunctionType;
import roy.types.ListType;
import roy.types.ObjectType;
import roy.types.TupleType;
import roy.types.Type;
import roy.types.TypeVariable;
import roy.types.UnionType;

/**
 *
 * @author hexaredecimal
 */
public class Parser {

	private List<Token> tokens;
	private List<Ast> nodes;
	private List<ErrorNode> errors;

	public Parser(List<Token> tokens) {
		this.tokens = tokens;
		this.nodes = new ArrayList<>();
	}

	public List<Ast> parse() {
		var top = peek(0);
		var err_loops = 0;
		while (!tokens.isEmpty() && top.kind != TokenKind.EOF) {
			top = peek(0);
			if (err_loops > 0) {
				Errors.reportSyntaxError(top, "Invalid token encountered: " + peek(0).kind);
			}

			if (match(TokenKind.KEYWORD) && top.text == "fn") {
				nodes.add(function());
				err_loops = 0;
				continue;
			}

			if (match(TokenKind.KEYWORD) && top.text == "type") {
				nodes.add(typeAlias());
				err_loops = 0;
				continue;
			}

			if (match(TokenKind.HASH)) {
				nodes.add(annotatedNode());
				continue;
			}

			if (match(TokenKind.EOF)) {
				break;
			}
			err_loops++;
		}

		// nodes.forEach(System.out::println);
		return nodes;
	}

	private Ast annotatedNode() {
		next();
		expect(TokenKind.LBRACKET, "Expected `[` after the `#`");
		List<String> annotations = new ArrayList<>();
		while (!match(TokenKind.RBRACKET)) {
			if (match(TokenKind.ID)) {
				var t = peek(0);
				annotations.add(t.text);
				next();

				if (match(TokenKind.COMMA)) {
					next();
				}
			}
		}
		expect(TokenKind.RBRACKET, "Expected `]` after the list of annotations");

		var t = peek(0);
		if (match(TokenKind.EOF)) {
			Errors.reportSyntaxError(t, "Expected fn after the annotation but found EOF instead");
		}

		if (!match(TokenKind.KEYWORD) && !t.text.equals("fn")) {
			Errors.reportSyntaxError(t, "Expected fn after the annotation but found " + t.text + " instead");
		}

		var func = annotatedFunction(annotations.contains("extern"));
		return new AnnotatedFunction(annotations, (RFunction) func);
	}

	private Ast annotatedFunction(boolean is_extern) {
		next();
		var name = expect(TokenKind.ID, "Expected an identifier for function name after the `fn` keyword");
		var args = collectArgs();

		Type ret_type = new TypeVariable(name.span);

		if (match(TokenKind.COLON)) {
			next();
			ret_type = parseType();
		}

		var t0 = peek(0);
		expect(TokenKind.ASSIGN, "Expected a `=` after the return type " + peek(0));
		var t = peek(0);

		if (t.kind == TokenKind.EOF) {
			expect(TokenKind.ERR, "Expected function to have a body when defining function `" + name.text + "`");
		}
		Ast body = null;

		if (is_extern) {
			var start = t0;
			StringBuilder sb = new StringBuilder();
			while (!match(TokenKind.SEMI_COLON)) {
				sb.append(peek(0).text).append(" ");
				if (match(TokenKind.EOF)) {
					Errors.reportSyntaxError(peek(0), "Expected `;` at the end of extern function body");
					System.exit(0);
				}
				next();
			}
			var end = peek(0);
			next();

			body = new JSCode(start, end, sb.toString());
		} else {
			body = expression();
		}

		t = peek(0);

		List<Ast> where = new ArrayList<Ast>();
		if (match(TokenKind.KEYWORD) && t.text.equals("where")) {
			if (is_extern) {
				Errors.reportSyntaxError(peek(0), "Extern functions cannot have where blocks");
			} else {
				where = collectWhereBlock();
			}
		}

		if (is_extern) {
			if (ret_type instanceof TypeVariable tv && !tv.is_user_defined) {
				Errors.reportSyntaxError(t0, "All arguments in extern function must be be explicitly typed. That also goes for the return type");
			}
			args.forEach(arg -> {
				if (arg.type instanceof TypeVariable tv && !tv.is_user_defined) {
					Errors.reportSyntaxError(arg.name, "All arguments in extern function must be be explicitly typed");
				}
			});
		}
		
		if (args.size() <= 1) {
			return new RFunction(name, args, ret_type, body, where);
		}

		var closure_arg = args.removeLast();
		var closure_args = new ArrayList<Arg>();
		closure_args.add(closure_arg);

		var closure = new RClosure(t, closure_args, ret_type, body);
		var argTypes = new ArrayList<Type>();
		argTypes.add(closure_arg.type);
		ret_type = new FunctionType(argTypes, ret_type);

		while (args.size() > 1) {
			closure_arg = args.removeLast();
			closure_args = new ArrayList<>();
			closure_args.add(closure_arg);

			argTypes = new ArrayList<>();
			argTypes.add(closure_arg.type);

			ret_type = new FunctionType(argTypes, ret_type);

			closure = new RClosure(t, closure_args, ret_type, closure);
		}

		body = closure;

		return new RFunction(name, args, ret_type, body, where);
	}

	private Ast typeAlias() {
		next();
		var name = expect(TokenKind.ID, "Expected an identifier for type alias name after the `type` keyword");
		expect(TokenKind.ASSIGN, "Expected a `=` after the alias name" + peek(0));
		var t = peek(0);

		if (t.kind == TokenKind.EOF) {
			expect(TokenKind.ERR, "Expected type alias to have a type after the `=` in when declaring type alias `" + name.text + "`");
		}
		var type = parseType();
		return new TypeAlias(name, type);
	}

	private Ast function() {
		next();
		var name = expect(TokenKind.ID, "Expected an identifier for function name after the `fn` keyword");
		var args = collectArgs();

		Type ret_type = new TypeVariable(name.span);

		if (match(TokenKind.COLON)) {
			next();
			ret_type = parseType();
		}

		expect(TokenKind.ASSIGN, "Expected a `=` after the return type " + peek(0));
		var t = peek(0);

		if (t.kind == TokenKind.EOF) {
			expect(TokenKind.ERR, "Expected function to have a body when defining function `" + name.text + "`");
		}
		var body = expression();

		t = peek(0);

		List<Ast> where = new ArrayList<Ast>();
		if (match(TokenKind.KEYWORD) && t.text.equals("where")) {
			where = collectWhereBlock();
		}

		if (args.size() <= 1) {
			return new RFunction(name, args, ret_type, body, where);
		}

		var closure_arg = args.removeLast();
		var closure_args = new ArrayList<Arg>();
		closure_args.add(closure_arg);
		var closure = new RClosure(t, closure_args, ret_type, body);
		var argTypes = new ArrayList<Type>();
		argTypes.add(closure_arg.type);
		ret_type = new FunctionType(argTypes, ret_type);

		while (args.size() > 1) {
			closure_arg = args.removeLast();
			closure_args = new ArrayList<>();
			closure_args.add(closure_arg);

			argTypes = new ArrayList<>();
			argTypes.add(closure_arg.type);

			ret_type = new FunctionType(argTypes, ret_type);

			closure = new RClosure(t, closure_args, ret_type, closure);
		}

		body = closure;

		return new RFunction(name, args, ret_type, body, where);
	}

	private List<Ast> collectWhereBlock() {
		next();
		var funcs = new ArrayList<Ast>();
		while (!match(TokenKind.SEMI_COLON)) {
			var top = peek(0);
			if (match(TokenKind.KEYWORD) && top.text == "fn") {
				funcs.add(function());
			} else if (top.kind == TokenKind.SEMI_COLON || top.kind == TokenKind.EOF) {
				break;
			} else {
				Errors.reportSyntaxError(top, "Invalid expression found in where block, only functions are allowed");
			}
		}
		next();
		return funcs;
	}

	private List<Arg> collectArgs() {
		var t = peek(0);
		var next = peek(1);

		var args = new ArrayList<Arg>();
		if (t.kind == TokenKind.LPAREN && next.kind == TokenKind.RPAREN) {
			next();
			next();
			return args;
		}

		do {
			if (match(TokenKind.LPAREN)) {
				next();
				args.add(collectTypedArg());
				continue;
			}

			if (match(TokenKind.COLON) || match(TokenKind.ASSIGN)) {
				break;
			}
			var arg = expect(TokenKind.ID, "Expected an identifier for an argument name in function declaration");
			args.add(new Arg(arg, new TypeVariable(arg.span)));
		} while (!match(TokenKind.COLON) && !match(TokenKind.ASSIGN) && !match(TokenKind.ARROW));

		return args;
	}

	private Arg collectTypedArg() {
		var name = expect(TokenKind.ID, "Expected an identifier for an argument name in function declaration");
		expect(TokenKind.COLON, "Expected a `:` after an argument name");
		var type = parseType();
		expect(TokenKind.RPAREN, "Expected a `)` after a type list");
		return new Arg(name, type);
	}

	private Type parseType() {
		var t0 = peek(0);
		var t = _parseType();
		List<Type> types = new ArrayList<>();
		while (!match(TokenKind.RPAREN) && !match(TokenKind.ASSIGN) && !match(TokenKind.COMMA) && !match(TokenKind.RBRACE) && !match(TokenKind.KEYWORD) && !match(TokenKind.HASH)) {
			var t1 = peek(0);
			if (match(TokenKind.BITWISE_OPERATOR) && t1.text == "|") {
				next();

				Type first = null;
				if (types.size() > 0 && t instanceof TypeVariable type) {
					first = new AppType(type.name, types);
				} else {
					first = t;
				}

				return parseUnionType(first);
			} else {
				types.add(_parseType());
			}
		}
		if (types.isEmpty()) {
			return t;
		}

		if (!(t instanceof TypeVariable)) {
			Errors.reportSyntaxError(t0, "Expected a name for a sum type when it being declared");
		}

		var name = ((TypeVariable) t).name;
		return new AppType(name, types);
	}

	private Type parseUnionType(Type first) {
		List<Type> types = new ArrayList<>();
		types.add(first);
		while (!match(TokenKind.RPAREN) && !match(TokenKind.ASSIGN) && !match(TokenKind.COMMA) && !match(TokenKind.RBRACE)) {
			var t1 = peek(0);
			types.add(_parseType());
			if (match(TokenKind.BITWISE_OPERATOR) && t1.text.equals("|")) {
				continue;
			} else {
				break;
			}
		}

		return new UnionType(types);
	}

	private Type _parseType() {
		var top = peek(0);
		if (match(TokenKind.ID)) {
			next();
			var vars = new ArrayList<Type>();
			if (Type.isPrimitive(top.text)) {
				return Type.toPrimitive(top.text);
			}
			return new TypeVariable(top, true);
		}

		if (match(TokenKind.LBRACKET)) {
			next();
			var inner = parseType();
			expect(TokenKind.RBRACKET, "Expect `]` after the type");
			return new ListType(inner);
		}

		if (match(TokenKind.LPAREN)) {
			List<Type> nodes = new ArrayList<>();
			next();
			var node = parseType();
			if (match(TokenKind.COMMA)) {
				nodes.add(node);
				while (match(TokenKind.COMMA)) {
					next();
					node = parseType();
					nodes.add(node);
				}
				expect(TokenKind.RPAREN, "Expected `)` at the end of tuple type");
				return new TupleType(nodes);
			}
			return node;
		}

		if (match(TokenKind.LBRACE)) {
			next();
			HashMap<Token, Type> obj = new HashMap<>();
			var t = peek(0);
			if (t.kind == TokenKind.RBRACE) {
				next();
				return new ObjectType(obj);
			}

			while (!match(TokenKind.RBRACE)) {
				var id = expect(TokenKind.ID, "Expected Identifier in Object type key");
				expect(TokenKind.COLON, "Expected `:` after field name in object literal");
				var type = parseType();
				obj.put(id, type);
				t = peek(0);
				if (t.kind == TokenKind.RBRACE) {
					break;
				}
				expect(TokenKind.COMMA, "Expected `,` after the expression in object literal");
			}

			next();
			return new ObjectType(obj);
		}

		It.todo("Found: " + peek(0).text);
		return null;
	}

	private Ast expression() {
		var top = peek(0);

		if (match(TokenKind.KEYWORD) && top.text.equals("let")) {
			return letsExpression();
		}

		var result = ternary();

		top = peek(0);
		if (match(TokenKind.KEYWORD) && top.text.equals("match")) {
			return matchExpression(result);
		}
		return result;
	}

	private Ast matchExpression(Ast match) {
		next();
		expect(TokenKind.LBRACE, "Braces required around match expression");
		var t = peek(0);
		List<MatchCase> cases = new ArrayList<>();
		while (match(TokenKind.BITWISE_OPERATOR) && t.text.equals("|")) {
			next();
			var p = parsePattern();
			expect(TokenKind.ARROW, "Expected `->` after pattern expression");
			var body = expression();
			cases.add(new MatchCase(p, body));
			t = peek(0);
		}

		t = peek(0);
		expect(TokenKind.RBRACE, "Expected closing brace at the end of match expression, but found `" + t.text + "`");
		return new Match(match, cases);
	}

	private Pattern parsePattern() {
		var pattern = _parsePattern();
		if (match(TokenKind.COLON)) {
			List<Pattern> points = new ArrayList<>();
			points.add(pattern);
			while (match(TokenKind.COLON)) {
				next();
				pattern = _parsePattern();
				points.add(pattern);
			}
			return new ListPattern(points);
		}
		return pattern;
	}

	private Pattern _parsePattern() {
		var t = peek(0);
		var next = peek(1);
		if (match(TokenKind.LBRACE)) {
			return objectPattern();
		} else if (match(TokenKind.LPAREN)) {
			return tuplePattern();
		}

		var expr = expression();
		return toPattern(expr);
	}

	private Pattern toPattern(Ast node) {
		List<Pattern> points = new ArrayList<>();

		if (node instanceof RObject obj) {
			for (var key : obj.obj.keySet()) {
				points.add(new ExpressionPattern(new Identifier(key)));
			}
			return new ObjectPattern(points);
		}

		if (node instanceof Tuple tuple) {
			for (var point : tuple.values) {
				points.add(new ExpressionPattern(point));
			}
			return new TuplePattern(points);
		}

		if (node instanceof Call call) {
			for (var point : call.params) {
				points.add(new ExpressionPattern(point));
			}
			return new ConsPattern(call.expr, points);
		}

		if (node instanceof Identifier id) {
			if (id.value.text.equals("_")) {
				return new AnyPattern();
			}
			return new BindPattern(id);
		}

		if (node instanceof roy.ast.Number || node instanceof RString || node instanceof BooleanValue) {
			return new ExpressionPattern(node);
		}

		var token = getErrTokenFromAst(node);
		Errors.reportSyntaxError(token, "Invalid pattern in match case");
		It.unreachable();
		return null;
	}

	public Token getErrTokenFromAst(Ast ast) {
		if (ast instanceof IfElse i) {
			return getErrTokenFromAst(i.cond);
		}
		if (ast instanceof Match m) {
			return getErrTokenFromAst(m.match);
		}
		if (ast instanceof BinOp b) {
			return getErrTokenFromAst(b.lhs);
		}
		if (ast instanceof roy.ast.Number n) {
			return n.value;
		}
		if (ast instanceof roy.ast.RString n) {
			return n.value;
		}
		if (ast instanceof roy.ast.BooleanValue n) {
			return n.value;
		}

		It.unreachable();
		return null;
	}

	private Pattern objectPattern() {
		List<Pattern> points = new ArrayList<>();
		next();
		while (!match(TokenKind.RBRACE)) {
			var pattern = parsePattern();
			var t = peek(0);
			points.add(pattern);
			if (t.kind == TokenKind.RPAREN) {
				break;
			}
			expect(TokenKind.COMMA, "Expected `,` between expressions object pattern");
		}
		next();
		return new ObjectPattern(points);
	}

	private Pattern tuplePattern() {
		List<Pattern> points = new ArrayList<>();

		next();
		while (!match(TokenKind.RBRACE)) {
			var pattern = parsePattern();
			var t = peek(0);
			points.add(pattern);
			if (t.kind == TokenKind.RPAREN) {
				break;
			}
			expect(TokenKind.COMMA, "Expected `,` between expressions object pattern");
		}
		next();
		return new TuplePattern(points);
	}

	private Ast blockOrObject() {
		next();

		var t = peek(0);
		var next = peek(1);
		if (match(TokenKind.ID) && next.kind == TokenKind.COLON) {
			return object();
		}

		if (match(TokenKind.ID) && (next.kind == TokenKind.ARROW || next.kind == TokenKind.ID || next.kind == TokenKind.LPAREN)) {
			return blockClosure();
		}

		if (match(TokenKind.LPAREN) && (next.kind == TokenKind.ID)) {
			return blockClosure();
		}

		if (match(TokenKind.ID) && t.text.equals("_") && next.kind == TokenKind.ARROW) {
			return blockClosure();
		}

		List<Ast> exprs = new ArrayList<>();
		while (!match(TokenKind.RBRACE)) {
			var expr = expression();
			exprs.add(expr);
			t = peek(0);
			if (t.kind == TokenKind.EOF) {
				expect(TokenKind.ERR, "Expected `}` at the end of block but found EOF.");
			}
		}

		next();
		return new Block(exprs);
	}

	private Ast blockClosure() {
		var t0 = peek(0);
		List<Arg> args = new ArrayList<>();
		if (!t0.text.equals("_")) {
			args = collectArgs();
		} else {
			next();
		}

		Type type = new TypeVariable(t0.span);
		if (match(TokenKind.COLON)) {
			next();
			type = parseType();
		}
		expect(TokenKind.ARROW, "Expected `->` after clocure argument list");

		List<Ast> exprs = new ArrayList<>();
		while (!match(TokenKind.RBRACE)) {
			var expr = expression();
			exprs.add(expr);
			var t = peek(0);
			if (t.kind == TokenKind.EOF) {
				expect(TokenKind.ERR, "Expected `}` at the end of block but found EOF.");
			}
		}

		Ast expr = new Block(exprs);
		if (exprs.size() == 1) {
			expr = exprs.removeFirst();
		}

		next();

		return new RClosure(t0, args, type, expr);
	}

	private Ast object() {
		// name : expr
		HashMap<Token, Ast> obj = new HashMap<>();
		while (match(TokenKind.ID)) {
			var name = peek(0);
			next();
			expect(TokenKind.COLON, "Expected `:` after field name in object literal");
			var expr = expression();
			var t = peek(0);
			obj.put(name, expr);
			if (t.kind == TokenKind.RBRACE) {
				break;
			}
			expect(TokenKind.COMMA, "Expected `,` after the expression in object literal");
		}

		next();

		return new RObject(obj);
	}

	private Ast letsExpression() {
		next();
		var let = letExpression();
		List<Let> lets = new ArrayList<>();
		var t = peek(0);
		var next = peek(1);
		lets.add(let);
		while (match(TokenKind.ID) && (next.kind == TokenKind.ASSIGN || next.kind == TokenKind.COLON)) {
			let = letExpression();
			lets.add(let);
		}

		t = peek(0);
		if (match(TokenKind.KEYWORD) && t.text.equals("in")) {
			next();
			return new LetIn(lets, expression());
		}

		if (lets.size() == 1) {
			var expr = ((Let) let).name;
			return new LetIn(lets, new Identifier(expr));
		}

		expect(TokenKind.ERR, "Expected `in` followed by expresssion after variable declaration list");
		return null;
	}

	private Let letExpression() {
		var name = expect(TokenKind.ID, "expected an identifier for the name of the variable being defined.");
		Type type = new TypeVariable(name.span);
		if (match(TokenKind.COLON)) {
			next();
			type = parseType();
		}
		expect(TokenKind.ASSIGN, "Expected a `=` in let expression");
		var expr = expression();
		return new Let(name, type, expr);
	}

	private Ast groupOrTuple() {
		var t = next();

		if (match(TokenKind.RPAREN)) {
			next();
			return new Unit();
		}

		var node = expression();
		List<Ast> nodes = new ArrayList<>();
		t = peek(0);
		if (match(TokenKind.COMMA)) {
			nodes.add(node);
			while (match(TokenKind.COMMA)) {
				next();
				node = expression();
				nodes.add(node);
			}
			expect(TokenKind.RPAREN, "Expected `)` after expression in tuple expression");
			return new Tuple(nodes);
		}
		expect(TokenKind.RPAREN, "Expected `)` after expression in group expression, `" + peek(0).text + "` found instead");
		return node;
	}

	private Ast ternary() {
		Ast term = booleanOperation();
		return term;
	}

	private Ast booleanOperation() {
		Ast result = additive();

		while (true) {
			var op = peek(0);
			if (match(TokenKind.BOOLEAN_OPERATOR)) {
				next();
				result = new BinOp(result, expression(), op);
				continue;
			}
			break;
		}

		return result;
	}

	private Ast additive() {
		Ast result = multiplicative();

		while (true) {
			var op = peek(0);
			if (match(TokenKind.ADDITIVE_OPERATOR) || match(TokenKind.STR_CONCAT_OPERATOR)) {
				next();
				result = new BinOp(result, expression(), op);
				continue;
			} else if (match(TokenKind.PIPE)) {
				next();
				var t = peek(0);
				var next = multiplicative();
				if (!(next instanceof Call) && !(next instanceof Identifier)) {
					Errors.reportSyntaxError(t, "Pipe operator can only be used with functions and symbols");
				}
				List<Ast> nodes = new ArrayList<>();
				// 5 |> hello
				if (next instanceof Identifier id) {
					nodes.add(id);
					result = new Call(result, nodes);
				} else if (next instanceof Call call) {
					// 5 |> add 10
					call.params.addFirst(result);
					result = call;
				}
			}
			break;
		}

		return result;
	}

	private Ast multiplicative() {
		Ast result = fieldAccess();

		while (true) {
			var op = peek(0);
			if (match(TokenKind.MULTPLICATIVE_OPERATOR)) {
				next();
				var rhs = expression();
				result = new BinOp(result, rhs, op);
				continue;
			}
			break;
		}

		return result;
	}

	private Ast fieldAccess() {
		Ast result = unary();
		while (true) {
			if (match(TokenKind.DOT)) {
				next();
				var t = peek(0);
				if (match(TokenKind.ID)) {
					next();
					result = new FieldAccess(result, new Identifier(t));
					continue;
				}
			}
			break;
		}

		return result;
	}

	private Ast unary() {
		var op = peek(0);
		if (match(TokenKind.ADDITIVE_OPERATOR) && op.text.equals("=")) {
			It.todo("Unary");
		}

		if (match(TokenKind.ADDITIVE_OPERATOR) && op.text.equals("-")) {
			next();
			var lhs = expression();
			var tok = new Token(TokenKind.NUMBER, "-1", op.span);
			op.kind = TokenKind.MULTPLICATIVE_OPERATOR;
			op.text = "*";
			return new BinOp(lhs, new roy.ast.Number(tok), op);
		}

		return call();
	}

	private boolean isCallValid() {
		return !match(TokenKind.EOF) && !match(TokenKind.RPAREN) && !match(TokenKind.COMMA)
			&& !match(TokenKind.KEYWORD) && !match(TokenKind.BOOLEAN_OPERATOR) && !match(TokenKind.RBRACE)
			&& !match(TokenKind.ADDITIVE_OPERATOR) && !match(TokenKind.MULTPLICATIVE_OPERATOR) && !match(TokenKind.COLON) && !match(TokenKind.ARROW)
			&& !match(TokenKind.STR_CONCAT_OPERATOR) && !match(TokenKind.PIPE) && !match(TokenKind.KEYWORD) && !match(TokenKind.SEMI_COLON)
			&& !match(TokenKind.DOT);
	}

	private Ast call() {
		Token t0 = peek(0);
		Ast result = conditional();

		// Check if there are any expressions following this one that could be arguments
		Token t = peek(0);
		if (isCallValid()) {

			List<Ast> args = new ArrayList<>();
			while (isCallValid()) {
				var t1 = peek(0);
				var current_line = t1.span.line;
				var prev_line = t.span.line;

				/*if (t1.span.line > t0.span.line || t1.span.line > t.span.line) { // You are now in a different expresssion
					break;
				}*/
				args.add(expression());

				// Stop if we've reached a token that would terminate the argument list
				t = peek(0);

				if (isCallValid()) {
					break;
				}
			}

			// Create a function call node with the result as the function and args as parameters
			if (!args.isEmpty()) {
				return new Call(result, args);
			}
		}

		return result;
	}

	private Ast conditional() {
		var top = peek(0);
		if (match(TokenKind.KEYWORD) && top.text.equals("if")) {
			return ifStatement();
		}
		Ast result = blockOrClosureOrObject();
		return result;
	}

	private Ast blockOrClosureOrObject() {
		if (match(TokenKind.LBRACE)) {
			return blockOrObject();
		}
		Ast result = groupExpression();
		return result;
	}

	private Ast groupExpression() {
		if (match(TokenKind.LPAREN)) {
			return groupOrTuple();
		}
		return parsePrimary();
	}

	private Ast ifStatement() {
		next();
		var cond = expression();
		var t = peek(0);
		if (!(match(TokenKind.KEYWORD) && t.text.equals("then"))) {
			expect(TokenKind.ERR, "Expected `then` after condition expresssion in if expression");
		}
		next();
		var then = expression();
		t = peek(0);
		if (!(match(TokenKind.KEYWORD) && t.text.equals("else"))) {
			expect(TokenKind.ERR, "If expression without else is not allowed");
		}
		next();

		var elze = expression();

		return new IfElse(cond, then, elze);
	}

	private Ast parsePrimary() {
		// Number, "Hello", etc
		var t = peek(0);
		if (match(TokenKind.NUMBER)) {
			next();
			return new roy.ast.Number(t);
		}

		if (match(TokenKind.STRING)) {
			next();
			return new RString(t);
		}

		if (match(TokenKind.ID)) {
			next();
			if (t.text.equals("true") || t.text.equals("false")) {
				return new BooleanValue(t);
			}
			return new Identifier(t);
		}

		It.unreachable("parsePrimary: " + t.text);
		return null;
	}

	private Token peek(int offset) {
		if (offset >= this.tokens.size()) {
			return new Token(TokenKind.EOF, null);
		}
		return tokens.get(offset);
	}

	private Token next() {
		assert !tokens.isEmpty();
		return tokens.removeFirst();
	}

	private boolean match(TokenKind type) {
		final Token current = peek(0);
		if (type != current.kind) {
			return false;
		}
		return true;
	}

	private Token expect(TokenKind type, String text) {
		var top = peek(0);
		if (match(type)) {
			next();
			return top;
		}

		Errors.reportSyntaxError(top, text);
		System.exit(0);
		//var err = new ErrorNode(top, text);
		// errors.add(err);
		return top;
	}

}
