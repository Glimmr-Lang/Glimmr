package roy.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import roy.ast.AnnotatedFunction;
import roy.ast.AnyPattern;
import roy.ast.Arg;
import roy.ast.Array;
import roy.ast.Ast;
import roy.ast.BinOp;
import roy.ast.BindPattern;
import roy.ast.Block;
import roy.ast.BooleanValue;
import roy.ast.Call;
import roy.ast.ConsPattern;
import roy.ast.ExpressionPattern;
import roy.ast.FieldAccess;
import roy.ast.GroupExpression;
import roy.ast.Identifier;
import roy.ast.IfElse;
import roy.ast.JSCode;
import roy.ast.Let;
import roy.ast.LetIn;
import roy.ast.ListPattern;
import roy.ast.When;
import roy.ast.MatchCase;
import roy.ast.ModuleAccess;
import roy.ast.ObjectPattern;
import roy.ast.Pattern;
import roy.ast.RAstList;
import roy.ast.RClosure;
import roy.ast.RFunction;
import roy.ast.RModule;
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
import roy.typechecker.TypeChecker;
import roy.types.AppType;
import roy.types.FunctionType;
import roy.types.ListType;
import roy.types.NamedType;
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
	public static List<Ast> typeAliases = new ArrayList<>();

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
				Errors.reportSyntaxError(top, "Invalid token encountered: " + peek(0).text);
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

			if (match(TokenKind.KEYWORD) && top.text == "import") {
				var node = importModule();
				if (node instanceof RAstList list) {
					list.decls.forEach(item -> nodes.add(item));
				} else {
					nodes.add(node);
				}
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

	public List<Ast> repl() {
		var top = peek(0);
		var err_loops = 0;
		while (!tokens.isEmpty() && top.kind != TokenKind.EOF) {
			top = peek(0);
			if (err_loops > 0) {
				Errors.reportSyntaxError(top, "Invalid token encountered: " + peek(0).text);
			} else if (match(TokenKind.KEYWORD) && top.text == "fn") {
				nodes.add(function());
				err_loops = 0;
				continue;
			} else if (match(TokenKind.KEYWORD) && top.text == "type") {
				nodes.add(typeAlias());
				err_loops = 0;
				continue;
			} else if (match(TokenKind.HASH)) {
				nodes.add(annotatedNode());
				continue;
			} else if (match(TokenKind.EOF)) {
				break;
			} else {
				nodes.add(expression());
				continue;
			}
			err_loops++;
		}

		// nodes.forEach(System.out::println);
		return nodes;
	}

	private Ast annotatedNode() {
		next();
		var top = expect(TokenKind.LBRACKET, "Expected `[` after the `#`");
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

		if (annotations.contains("extern")) {
			var func = annotatedFunction(annotations.contains("extern"));
			return new AnnotatedFunction(annotations, (RFunction) func);
		}

		if (annotations.contains("export")) {
			var func = function();
			return new AnnotatedFunction(annotations, (RFunction) func);
		}

		var str = String.join(", ", annotations);
		Errors.reportSyntaxError(top, "Invalid annotations found. Expected extern or export but found `" + str + "`");
		return null;
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
			var start = name;
			StringBuilder sb = new StringBuilder();
			while (!match(TokenKind.SEMI_COLON)) {
				if (match(TokenKind.EOF)) {
					Errors.reportSyntaxError(peek(0), "Expected `;` at the end of extern function body");
					System.exit(0);
				}
				next();
			}
			var end = peek(0);
			next();

			var lines = Fs
				.readToString(start.span.filepath)
				.unwrap()
				.lines()
				.toList();

			var started = false;
			for (int i = start.span.line; i < lines.size(); i++) {
				var line = lines.get(i).trim();
				if (line.contains(";")) {
					if (line.length() > 1) {
						sb.append(line);
					}
					break;
				}
				sb.append(line);

				if (line.contains("if")
					|| line.contains("else")
					|| line.contains("while")
					|| line.contains("do")
					|| line.contains("function")
					|| line.contains("try")
					|| line.contains("catch")
					|| line.contains("switch")
					|| line.contains("{")
					|| line.contains("}")) {
					sb.append("\n");
					continue;
				}
				sb.append(";\n");
			}

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

		if (args.isEmpty()) {
			return new RFunction(name, args, ret_type, body, where);
		} else if (args.size() == 1) {
			return new RFunction(name, args, ret_type, body, where);
		}

		var closure_arg = args.removeLast();
		var closure_args = new ArrayList<Arg>();
		closure_args.add(closure_arg);

		var closure = new RClosure(t, closure_args, ret_type, body);
		closure.no_return = true;
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

	private Ast importModule() {
		next();
		List<String> path = new ArrayList<>();

		var id = expect(TokenKind.ID, "expected identifier for module name");
		path.add(id.text);

		while (match(TokenKind.DBL_COLON)) {
			next();
			id = expect(TokenKind.ID, "expected identifier for module name");
			path.add(id.text);
		}

		var import_path = "./" + String.join("/", path.toArray(String[]::new));

		var mod = processImport(import_path, id);
		if (match(TokenKind.LBRACE)) {
			return extractNodes((RModule) mod, id);
		}
		return mod;
	}

	private Ast extractNodes(RModule module, Token id) {
		next();

		if (match(TokenKind.RBRACE)) {
			expect(TokenKind.ERR, "Empty import list is not allowed. Remove the `{}`");
		}

		Set<Token> path = new HashSet<>();

		while (!match(TokenKind.RBRACE)) {
			var node = expect(TokenKind.ID, "expected identifier for function or type name");
			checkDuplicates(node, path);
			path.add(node);
			if (match(TokenKind.RBRACE)) {
				break;
			}
			expect(TokenKind.COMMA, "Expected a `,` between import items");
		}
		next();

		List<Ast> lifted_nodes = new ArrayList<>();
		for (var names : path) {
			lifted_nodes.add(extractNode(module.name.text, module.decls, names));
		}

		return new RAstList(lifted_nodes);
	}

	private void checkDuplicates(Token name, Set<Token> nodes) {
		for (var node : nodes) {
			if (node.text.equals(name.text)) {
				Errors.reportSyntaxError(name, "Duplicate entries are anot allowed");
			}
		}
	}

	private Ast extractNode(String module, List<Ast> nodes, Token name) {
		for (var ast : nodes) {
			if (ast instanceof RFunction func && func.name.text.equals(name.text)) {
				return func;
			}
			if (ast instanceof AnnotatedFunction afunc && afunc.func.name.text.equals(name.text)) {
				return afunc;
			}
			if (ast instanceof TypeAlias ta && ta.name.text.equals(name.text)) {
				return ta;
			}
		}

		Errors.reportSyntaxError(name, "No function or type named `" + name.text + "` found in module " + module);
		It.unreachable();
		return null;
	}

	private Ast processImport(String path, Token id) {
		var fp = new File(path);
		if (fp.isFile()) {
			Errors.reportSyntaxError(id, "Import path leads to a file. Modules are expected to be directories");
		}

		List<Ast> nodes = new ArrayList<>();
		for (var file : fp.listFiles()) {
			if (file.getName().endsWith(".glmr")) {
				var ip = file.getAbsolutePath();
				nodes.addAll(_import(ip));
			}
		}

		if (nodes.isEmpty()) {
			Errors.reportSyntaxError(id, "Importing an empty module is reduntant, hence not allowed");
		}

		return new RModule(id, nodes);
	}

	private List<Ast> _import(String path) {
		Lexer lexer = new Lexer(path);
		var tokens = lexer.lex();

		//tokens.forEach(System.out::println);
		///*
		var parser = new Parser(tokens);
		var nodes = parser.parse();

		var typechecker = new TypeChecker(nodes, Parser.typeAliases);
		typechecker.process();
		typeAliases.addAll(typechecker.getAliases());
		return nodes;
	}

	private Ast typeAlias() {
		next();
		var name = expect(TokenKind.ID, "Expected an identifier for type alias name after the `type` keyword");
		if (!Character.isUpperCase(name.text.charAt(0))) {
			Errors.reportSyntaxError(name, "Type aliases are must start with an uppercase letter");
		}
		expect(TokenKind.ASSIGN, "Expected a `=` after the alias name");
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

		var eq = expect(TokenKind.ASSIGN, "Expected a `=` after the return type " + peek(0));
		var t = peek(0);

		if (t.kind == TokenKind.EOF) {
			Errors.reportSyntaxError(eq, "Expected function to have a body when defining function `" + name.text + "`");
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
		while (!match(TokenKind.RPAREN) && !match(TokenKind.ASSIGN) && !match(TokenKind.COMMA) && !match(TokenKind.RBRACE) && !match(TokenKind.KEYWORD) && !match(TokenKind.HASH) && !match(TokenKind.EOF) && !(match(TokenKind.RBRACKET))) {
			var t1 = peek(0);
			if (match(TokenKind.BITWISE_OPERATOR) && t1.text == "|") {
				next();

				if (match(TokenKind.EOF)) {
					Errors.reportSyntaxError(peek(0), "Found EOF while parsing sum type");
				}

				Type first = null;
				if (types.size() > 0 && t instanceof NamedType type) {
					first = new AppType(type.name, types);
				} else if (types.size() > 0 && t instanceof TypeVariable type) {
					first = new AppType(type.name, types);
				} else {
					first = t;
				}

				return parseUnionType(first);
			} else if (match(TokenKind.ARROW)) {
				return parseFunctionType(t);
			} else {
				types.add(_parseType());
			}
		}
		if (types.isEmpty()) {
			return t;
		}

		if (!(t instanceof TypeVariable) && !(t instanceof NamedType)) {
			Errors.reportSyntaxError(t0, "Expected a name for a sum type when it being declared ");
		}

		Function<?, ?> die = (o) -> {
			It.unreachable();
			return null;
		};

		var name = t instanceof TypeVariable ty
			? ty.name : t instanceof NamedType nty
				? nty.name : (Token) die.apply(null);
		return new AppType(name, types);
	}

	private Type parseFunctionType(Type first) {
		// Already got the first type before the ->
		next(); // Consume the ->

		// Parse the right-hand side (which could be another function type)
		Type returnType = parseType();

		// Create a function type with the first type as the argument
		// and the return type we just parsed
		List<Type> argTypes = new ArrayList<>();
		argTypes.add(first);
		return new FunctionType(argTypes, returnType);
	}

	private Type parseUnionType(Type first) {
		List<Type> types = new ArrayList<>();
		types.add(first);
		while (!match(TokenKind.RPAREN) && !match(TokenKind.ASSIGN) && !match(TokenKind.COMMA) && !match(TokenKind.RBRACE)) {
			var t1 = peek(0);
			types.add(parseType());
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
			if (Type.isPrimitive(top.text)) {
				return Type.toPrimitive(top.text);
			}
			if (Character.isUpperCase(top.text.charAt(0))) {
				return new NamedType(top);
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

		return result;
	}

	private Ast matchExpression() {
		next();
		var match = expression();
		expect(TokenKind.LBRACE, "Braces required around match expression");
		var t = peek(0);
		List<MatchCase> cases = new ArrayList<>();
		while (match(TokenKind.KEYWORD) && t.text.equals("is")){
			next();
			var p = parsePattern();
			expect(TokenKind.ARROW, "Expected `->` after pattern expression");
			var body = expression();
			cases.add(new MatchCase(p, body));
			t = peek(0);
		}

		t = peek(0);
		Ast body = null;
		if (match(TokenKind.KEYWORD) && t.text.equals("else")) {
			next();
			expect(TokenKind.ARROW, "Expected `->` after pattern expression");
			body = expression();
		}
		t = peek(0);
		expect(TokenKind.RBRACE, "Expected closing brace at the end of match expression, but found `" + t.text + "`");
		return new When(match, cases, body);
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
		var expr = expression();
		return toPattern(expr);
	}

	private Pattern toPattern(Ast node) {
		List<Pattern> points = new ArrayList<>();

		if (node instanceof RObject obj) {
			HashMap<Identifier, Ast> object = new HashMap<>();
			for (var kv : obj.obj.entrySet()) {
				var key = kv.getKey();
				var value = kv.getValue();
				object.put(new Identifier(key), value);
			}
			return new ObjectPattern(object);
		}

		if (node instanceof Tuple tuple) {
			for (var point : tuple.values) {
				points.add(toPattern(point));
			}
			return new TuplePattern(points);
		}

		if (node instanceof Call call) {
			for (var point : call.params) {
				points.add(toPattern(point));
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
		if (ast instanceof When m) {
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



	private Ast blockOrObject() {
		next();

		var t = peek(0);
		var next = peek(1);
		var next2 = peek(2);

		// {| it -> 
		if (match(TokenKind.ID) && (next.kind == TokenKind.ARROW)) {
			return blockClosure();
		}

		// {| it: ( -> 
		if (match(TokenKind.ID) && (next.kind == TokenKind.COLON) && next.kind == TokenKind.LPAREN) {
			return blockClosure();
		}


		if (match(TokenKind.ID) && next.kind == TokenKind.COLON) {
			return object();
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
		return new GroupExpression(node);
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
				result = new BinOp(result, multiplicative(), op);
				continue;
			} else if (match(TokenKind.PIPE)) {
				result = processPipe(result);
				continue;
			}
			break;
		}

		return result;
	}

	private Ast processPipe(Ast lhs) {
		next(); // Consume |>

		// For multi-line pipes, we need to handle newlines
		var rhs = callOrTerm();

		if (rhs instanceof Identifier id) {
			// Case 1: RHS is an identifier like "sbString"
			List<Ast> args = new ArrayList<>();
			args.add(lhs);
			return new Call(id, args);
		} else if (rhs instanceof ModuleAccess ma) {
			// Case 2: RHS is a module access like "io::println"
			List<Ast> args = new ArrayList<>();
			args.add(lhs);
			return new Call(ma, args);
		} else if (rhs instanceof Call call) {
			// Case 3: RHS is a function call like "append "Hello""
			call.params.addFirst(lhs);
			return call;
		} else if (rhs instanceof RClosure closure) {
			// Case 4: RHS is a closure like {x -> ...}
			// Create a call with the closure as the function and lhs as the argument
			List<Ast> args = new ArrayList<>();
			args.add(lhs);
			return new Call(closure, args);
		} else {
			// Error case - report it
			Errors.reportSyntaxError(peek(0), "Pipe operator requires identifier, function call, or closure on right side");
			return lhs;
		}
	}

	private Ast callOrTerm() {
		// Parse a function call or a simple term
		// This is used by the pipe operator to get the right-hand side
		Token functionToken = peek(0);

		if (match(TokenKind.ID)) {
			next(); // Consume the identifier

			// Check for module access (::)
			if (match(TokenKind.DBL_COLON)) {
				next(); // Consume ::
				
				// Expect an identifier after ::
				var moduleTarget = expect(TokenKind.ID, "Expected identifier after '::'");
				var moduleExpr = new ModuleAccess(new Identifier(functionToken), new Identifier(moduleTarget));
				
				// Check if module access is followed by parentheses for a function call
				if (match(TokenKind.LPAREN)) {
					next(); // Consume the opening parenthesis
					List<Ast> args = new ArrayList<>();
					
					// Parse comma-separated arguments inside parentheses
					if (!match(TokenKind.RPAREN)) {
						args.add(expression());
						
						while (match(TokenKind.COMMA)) {
							next(); // Consume the comma
							args.add(expression());
						}
					}
					
					// Expect closing parenthesis
					expect(TokenKind.RPAREN, "Expected ')' after function arguments");
					
					// Create a function call with the parsed arguments
					return new Call(moduleExpr, args);
				}
				
				// Just a module access with no args
				return moduleExpr;
			}
			
			// Check if identifier is followed by parentheses for a function call
			if (match(TokenKind.LPAREN)) {
				next(); // Consume the opening parenthesis
				List<Ast> args = new ArrayList<>();
				
				// Parse comma-separated arguments inside parentheses
				if (!match(TokenKind.RPAREN)) {
					args.add(expression());
					
					while (match(TokenKind.COMMA)) {
						next(); // Consume the comma
						args.add(expression());
					}
				}
				
				// Expect closing parenthesis
				expect(TokenKind.RPAREN, "Expected ')' after function arguments");
				
				// Create a function call with the parsed arguments
				return new Call(new Identifier(functionToken), args);
			}
			
			// Just an identifier with no args
			return new Identifier(functionToken);
		}
		
		// Check for block/closure
		if (match(TokenKind.LBRACE)) {
			return blockOrObject();
		}
		
		// Not an identifier or closure, just return a term
		return parseTerm();
	}

	private Ast multiplicative() {
		Ast result = unary();

		while (true) {
			var op = peek(0);
			if (match(TokenKind.MULTPLICATIVE_OPERATOR)) {
				next();
				var rhs = unary();
				result = new BinOp(result, rhs, op);
				continue;
			}
			break;
		}

		return result;
	}

	private Ast unary() {
		var op = peek(0);
		if (match(TokenKind.ADDITIVE_OPERATOR) && op.text.equals("-")) {
			next();
			var lhs = parseTerm();
			var tok = new Token(TokenKind.NUMBER, "-1", op.span);
			op.kind = TokenKind.MULTPLICATIVE_OPERATOR;
			op.text = "*";
			return new BinOp(lhs, new roy.ast.Number(tok), op);
		}

		return fieldAccess();
	}

	private boolean isCallValid() {
		// First check all the standard token types that terminate arguments
		if (match(TokenKind.EOF) || match(TokenKind.RPAREN) || match(TokenKind.COMMA)
			|| match(TokenKind.BOOLEAN_OPERATOR) || match(TokenKind.RBRACE)
			|| match(TokenKind.ADDITIVE_OPERATOR) || match(TokenKind.MULTPLICATIVE_OPERATOR) || match(TokenKind.COLON) || match(TokenKind.ARROW)
			|| match(TokenKind.STR_CONCAT_OPERATOR) || match(TokenKind.PIPE) || match(TokenKind.SEMI_COLON)
			|| match(TokenKind.DOT) || match(TokenKind.HASH) || match(TokenKind.DBL_COLON)) {
			return false;
		}

		var keywords = List.of("then", "else", "in", "let", "type", "fn", "match", "where", "try");
		// Keywords that terminate argument lists
		if (match(TokenKind.KEYWORD)) {
			var text = peek(0).text;
			if (keywords.contains(text)) {
				return false;
			}
		}

		return true;
	}

	private Ast fieldAccess() {
		Ast result = call();
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

	private Ast call() {
		Token functionToken = peek(0);
		Ast result = moduleAccess();

		// Handle function calls with parentheses
		if (match(TokenKind.LPAREN)) {
			next(); // Consume the opening parenthesis
			List<Ast> args = new ArrayList<>();
			
			// Parse comma-separated arguments inside parentheses
			if (!match(TokenKind.RPAREN)) {
				args.add(expression());
				
				while (match(TokenKind.COMMA)) {
					next(); // Consume the comma
					args.add(expression());
				}
			}
			
			// Expect closing parenthesis
			expect(TokenKind.RPAREN, "Expected ')' after function arguments");
			
			result = new Call(result, args);
		}

		return result;
	}

	private Ast parseTerm() {
		if (match(TokenKind.NUMBER) || match(TokenKind.STRING) || match(TokenKind.ID)) {
			return parsePrimary();
		}

		if (match(TokenKind.LPAREN)) {
			return groupOrTuple();
		}

		if (match(TokenKind.LBRACE)) {
			return blockOrObject();
		}

		var top = peek(0);
		if (match(TokenKind.KEYWORD) && top.text.equals("if")) {
			return ifStatement();
		}

		Errors.reportSyntaxError(peek(0), "Expected an expression but found: " + peek(0).text);
		return null;
	}

	private Ast moduleAccess() {
		var t0 = peek(0);
		Ast result = conditional();
		while (true) {
			if (match(TokenKind.DBL_COLON)) {
				next();
				if (!(result instanceof Identifier)) {
					Errors.reportTypeCheckError(t0, "Expected an identifier at the left side of the `::`");
				}
				var t = peek(0);
				if (match(TokenKind.ID)) {
					next();
					result = new ModuleAccess(result, new Identifier(t));
					
					// After creating a ModuleAccess, check if it's followed by function call parentheses
					if (match(TokenKind.LPAREN)) {
						next(); // Consume the opening parenthesis
						List<Ast> args = new ArrayList<>();
						
						// Parse comma-separated arguments inside parentheses
						if (!match(TokenKind.RPAREN)) {
							args.add(expression());
							
							while (match(TokenKind.COMMA)) {
								next(); // Consume the comma
								args.add(expression());
							}
						}
						
						// Expect closing parenthesis
						expect(TokenKind.RPAREN, "Expected ')' after function arguments");
						
						// Create a function call with the parsed arguments
						result = new Call(result, args);
					}
					
					continue;
				}
			}
			break;
		}
		return result;
	}

	private Ast conditional() {
		var top = peek(0);
		if (match(TokenKind.KEYWORD) && top.text.equals("if")) {
			return ifStatement();
		}
		Ast result = whenExpression();
		return result;
	}

	private Ast whenExpression() {
		var top = peek(0);
		if (match(TokenKind.KEYWORD) && top.text.equals("when")) {
			return matchExpression();
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
		return arrayExpression();
	}

	private Ast arrayExpression() {
		if (match(TokenKind.LBRACKET)) {
			return arrayExpr();
		}
		return parsePrimary();
	}
	
	private Ast arrayExpr() {
		var top = peek(0);
		next();
		List<Ast> elements = new ArrayList<>();
		while (!match(TokenKind.RBRACKET)) {
			elements.add(expression());
			if (match(TokenKind.RBRACKET)) {
				break;
			} else {
				expect(TokenKind.COMMA, "Expected a comma after the expression in array");
			}
		}
		next();
		return new Array(elements, top);
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

		It.unreachable("parsePrimary: " + t.text + " " + t.span.line);
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
		return top;
	}

}

