package roy.parser;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.Soundbank;
import roy.ast.Arg;
import roy.ast.Ast;
import roy.ast.BinOp;
import roy.ast.BooleanValue;
import roy.ast.Identifier;
import roy.ast.IfElse;
import roy.ast.Let;
import roy.ast.LetIn;
import roy.ast.RClosure;
import roy.ast.RFunction;
import roy.ast.RString;
import roy.ast.Tuple;
import roy.errors.ErrorNode;
import roy.errors.Errors;
import roy.rt.It;
import roy.tokens.Token;
import roy.tokens.TokenKind;
import roy.types.AppType;
import roy.types.FunctionType;
import roy.types.ListType;
import roy.types.Type;
import roy.types.TypeVariable;

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
		while (!tokens.isEmpty() && top.kind != TokenKind.EOF) {
			top = peek(0);
			if (match(TokenKind.KEYWORD) && top.text == "fn") {
				nodes.add(function());
			}
		}

		// nodes.forEach(System.out::println);
		return nodes;
	}

	private Ast function() {
		next();
		var name = expect(TokenKind.ID, "expected an identifier for function name after the `fn` keyword");
		var args = collectArgs();

		Type ret_type = new TypeVariable(name.span);

		if (match(TokenKind.COLON)) {
			next();
			ret_type = parseType();
		}

		expect(TokenKind.ASSIGN, "Expected a `=` after the return type " + peek(0));
		var t = peek(0);
		var body = expression();

		if (args.size() <= 1) {
			return new RFunction(name, args, ret_type, body);
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
		return new RFunction(name, args, ret_type, body);
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
		} while (!match(TokenKind.COLON) && !match(TokenKind.ASSIGN));

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
		var t = _parseType();
		List<Type> types = new ArrayList<>();
		while (!match(TokenKind.RPAREN) && !match(TokenKind.ASSIGN)) {
			types.add(_parseType());
		}
		if (types.isEmpty()) {
			return t;
		}

		return new AppType(t, types);
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

		It.todo();
		return null;
	}

	private Ast expression() {
		var top = peek(0);
		if (match(TokenKind.LPAREN)) {
			return groupOrTuple();
		}

		if (match(TokenKind.KEYWORD) && top.text.equals("let")) {
			return letsExpression();
		}

		var result = ternary();
		return result;
	}

	private Ast letsExpression() {
		next();
		var let = letExpression();
		List<Ast> lets = new ArrayList<>();
		var t = peek(0);
		var next = peek(1);
		lets.add(let);
		while (match(TokenKind.ID) && (next.kind == TokenKind.ASSIGN || next.kind == TokenKind.COLON)) {
			let = letExpression();
			lets.add(let);
		}

		if (lets.size() == 1) {
			return new LetIn(lets, let);
		}

		t = peek(0);
		if (match(TokenKind.KEYWORD) && t.text.equals("in")) {
			next();
			return new LetIn(lets, expression());
		}

		expect(TokenKind.ERR, "Expected `in` followed by expresssion after variable declaration list");
		return null;
	}

	private Ast letExpression() {
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
		expect(TokenKind.RPAREN, "Expected `)` after expression in group expression");
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
				result = new BinOp(result, additive(), op);
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
				result = new BinOp(result, multiplicative(), op);
				continue;
			}
			break;
		}

		return result;
	}

	private Ast multiplicative() {
		Ast result = unary();

		while (true) {
			var op = peek(0);
			if (match(TokenKind.MULTPLICATIVE_OPERATOR)) {
				next();
				result = new BinOp(result, unary(), op);
				continue;
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

		return call();
	}

	private Ast call() {
		Ast result = conditional();
		return result;
	}

	private Ast conditional() {
		var top = peek(0);
		if (match(TokenKind.KEYWORD) && top.text.equals("if")) {
			return ifStatement();
		}
		Ast result = parsePrimary();
		return result;
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

		Errors.reportSynaxError(top, text);
		System.exit(0);
		//var err = new ErrorNode(top, text);
		// errors.add(err);
		return top;
	}

}
