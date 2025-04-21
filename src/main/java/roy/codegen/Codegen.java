package roy.codegen;

import roy.codegen.jsast.JArray;
import roy.codegen.jsast.WhenExpression;
import java.lang.invoke.CallSite;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import roy.ast.AnnotatedFunction;
import roy.ast.Array;
import roy.ast.Ast;
import roy.ast.BinOp;
import roy.ast.Block;
import roy.ast.Call;
import roy.ast.FieldAccess;
import roy.ast.GroupExpression;
import roy.ast.Identifier;
import roy.ast.IfElse;
import roy.ast.JSCode;
import roy.ast.LetIn;
import roy.ast.ModuleAccess;
import roy.ast.Number;
import roy.ast.RClosure;
import roy.ast.RFunction;
import roy.ast.RObject;
import roy.ast.RString;
import roy.ast.Tuple;
import roy.ast.TypeAlias;
import roy.ast.Unit;
import roy.ast.When;
import roy.codegen.jsast.CodegenAst;
import roy.codegen.jsast.JBinOp;
import roy.codegen.jsast.JBlock;
import roy.codegen.jsast.JClosure;
import roy.codegen.jsast.JFunction;
import roy.codegen.jsast.JGroupExpression;
import roy.codegen.jsast.JIdentifier;
import roy.codegen.jsast.JIfElse;
import roy.codegen.jsast.JLetExpression;
import roy.codegen.jsast.JStatementList;
import roy.codegen.jsast.JCall;
import roy.codegen.jsast.JFieldAccess;
import roy.codegen.jsast.JObjInstance;
import roy.codegen.jsast.JObject;
import roy.codegen.jsast.JStringCodeEmbed;
import roy.codegen.jsast.NumberLiteral;
import roy.codegen.jsast.StringLiteral;
import roy.codegen.jsast.UnitLiteral;
import roy.codegen.jsast.WhenCases;
import roy.rt.It;
import roy.tokens.Token;
import roy.tokens.TokenKind;

public class Codegen {

	private List<Ast> nodes;

	public Codegen(List<Ast> nodes) {
		this.nodes = nodes;
	}

	public String gen() {
		List<CodegenAst> stmts = new ArrayList<>();
		for (var node : nodes) {
			if (node instanceof RFunction func) {
				stmts.add(codegenFunction(func));
			}

			if (node instanceof AnnotatedFunction afunc) {
				stmts.add(codegenAnnotatedFunction(afunc));
			}
		}

		return Arrays.stream(stmts.toArray())
						.map(Object::toString)
						.collect(Collectors.joining("\n"));
	}

	public String repl() {
		List<CodegenAst> stmts = new ArrayList<>();
		for (var node : nodes) {
			if (node instanceof RFunction func) {
				stmts.add(codegenFunction(func));
			} else if (node instanceof AnnotatedFunction afunc) {
				stmts.add(codegenAnnotatedFunction(afunc));
			} else if (!(node instanceof TypeAlias)) {
				stmts.add(codegenExpr(node));
			}
		}

		return Arrays.stream(stmts.toArray())
						.map(Object::toString)
						.collect(Collectors.joining("\n"));
	}

	private CodegenAst codegenAnnotatedFunction(AnnotatedFunction afunc) {
		var func = afunc.func;
		var name = func.name.text;
		var arg = func.args.isEmpty() ? null : func.args.getFirst();
		var body = codegenExpr(func.body);

		if (afunc.isExport) {
			List<CodegenAst> wheres = new ArrayList<>();
			for (var where : func.where) {
				var fx = codegenFunction((RFunction) where);
				wheres.add(fx);
			}

			if (!wheres.isEmpty()) {
				wheres.add(body);
				body = new JStatementList(wheres);
			}
		}

		var arg_name = arg == null ? "" : arg.name.text;
		var fx = new JFunction(name, arg_name, body);
		fx.export = afunc.isExport;
		return fx;
	}

	private CodegenAst codegenFunction(RFunction func) {
		var name = func.name.text;
		var arg = func.args.isEmpty() ? null : func.args.getFirst();
		var body = codegenExpr(func.body);

		List<CodegenAst> wheres = new ArrayList<>();
		for (var where : func.where) {
			var fx = codegenFunction((RFunction) where);
			wheres.add(fx);
		}

		if (!wheres.isEmpty()) {
			wheres.add(body);
			body = new JStatementList(wheres);
		}

		var arg_name = arg == null ? "" : arg.name.text;
		var fx = new JFunction(name, arg_name, body);
		if (name.equals("main")) {
			List<CodegenAst> statements = new ArrayList<>();
			statements.add(fx);
			statements.add(new JCall(new JIdentifier(name), new ArrayList<>()));
			return new JStatementList(statements);
		}
		return fx;
	}

	private CodegenAst codegenExpr(Ast expr) {
		if (expr instanceof roy.ast.Number number) {
			return codegenNumber(number);
		}

		if (expr instanceof Unit unit) {
			return codegenUnit(unit);
		}

		if (expr instanceof BinOp binop) {
			return codegenBinop(binop);
		}

		if (expr instanceof Identifier id) {
			return codegenIdentifier(id);
		}

		if (expr instanceof RClosure closure) {
			return codegenClosure(closure);
		}

		if (expr instanceof GroupExpression group) {
			return codegenGroup(group);
		}

		if (expr instanceof LetIn letin) {
			return codegenLetIn(letin);
		}

		if (expr instanceof Block block) {
			return codegenBlock(block);
		}

		if (expr instanceof IfElse ifelse) {
			return codegenIfElse(ifelse);
		}

		if (expr instanceof Call call) {
			return codegenCall(call);
		}

		if (expr instanceof JSCode code) {
			return codegenJsCode(code);
		}

		if (expr instanceof RString string) {
			return codegenString(string);
		}

		if (expr instanceof RObject obj) {
			return codegenObject(obj);
		}

		if (expr instanceof FieldAccess fa) {
			return codegenFieldAccess(fa);
		}

		if (expr instanceof ModuleAccess ma) {
			return codegenModuleAccess(ma);
		}

		if (expr instanceof When when) {
			return codegenWhenExpression(when);
		}

		if (expr instanceof Array array) {
			return codegenArray(array);
		}

		if (expr instanceof Tuple tuple) {
			return codegenTuple(tuple);
		}

		It.todo(expr.getClass().getName());
		return null;
	}

	private CodegenAst codegenTuple(Tuple tuple) {
		List<CodegenAst> params = new ArrayList<>();
		var expr = codegenExpr(new Identifier(new Token(TokenKind.ID, "Tuple")));
		for (var param : tuple.values) {
			params.add(codegenExpr(param));
		}
		return new JObjInstance(expr.toString(), params);
	}

	private CodegenAst codegenArray(Array array) {
		List<CodegenAst> nodes = new ArrayList<>();
		for (var node : array.elements) {
			nodes.add(codegenExpr(node));
		}

		return new JArray(nodes);
	}

	private CodegenAst codegenWhenExpression(When when) {
		var cond = codegenExpr(when.match);
		List<WhenCases> cases = new ArrayList<>();
		for (var match : when.cases) {
			var lhs = match.pattern;
			var rhs = codegenExpr(new Block(List.of(match.body)));
			cases.add(new WhenCases(lhs, rhs));
		}

		CodegenAst _elze = null;
		if (when._else != null) {
			_elze = codegenExpr(new Block(List.of(when._else)));
		}
		return new WhenExpression(cond, cases, _elze);
	}

	private CodegenAst codegenModuleAccess(ModuleAccess ma) {
		//var expr = codegenExpr(ma.module);
		return new JIdentifier(ma.field.value.text);
	}

	private CodegenAst codegenFieldAccess(FieldAccess field) {
		var expr = codegenExpr(field.obj);
		return new JFieldAccess(expr, field.field.value.text);
	}

	private CodegenAst codegenObject(RObject obj) {
		HashMap<String, CodegenAst> _obj = new HashMap<>();
		for (var kv : obj.obj.entrySet()) {
			var expr = codegenExpr(kv.getValue());
			_obj.put(kv.getKey().text, expr);
		}
		return new JObject(_obj);
	}

	private CodegenAst codegenString(RString string) {
		return new StringLiteral(string.value.text);
	}

	private CodegenAst codegenJsCode(JSCode code) {
		return new JStringCodeEmbed(code.code);
	}

	private CodegenAst codegenUnit(Unit unit) {
		return new UnitLiteral();
	}

	private CodegenAst codegenCall(Call call) {
		List<CodegenAst> params = new ArrayList<>();
		var expr = codegenExpr(call.expr);
		for (var param : call.params) {
			params.add(codegenExpr(param));
		}

		if (call.sum) {
			return new JObjInstance(call.expr.toString(), params);
		}
		return new JCall(expr, params);
	}

	private CodegenAst codegenIfElse(IfElse ifelse) {
		var cond = codegenExpr(ifelse.cond);
		var then = codegenExpr(ifelse.then);
		var elze = codegenExpr(ifelse.elze);
		return new JIfElse(cond, then, elze);
	}

	private CodegenAst codegenBlock(Block block) {
		List<CodegenAst> statements = new ArrayList<>();
		for (var expr : block.exprs) {
			var value = codegenExpr(expr);
			statements.add(value);
		}
		return new JBlock(statements);
	}

	private CodegenAst codegenLetIn(LetIn letin) {
		List<CodegenAst> statements = new ArrayList<>();
		for (var let : letin.lets) {
			var name = let.name.text;
			var value = codegenExpr(let.expr);
			statements.add(new JLetExpression(name, value));
		}
		statements.add(codegenExpr(letin.in));
		return new JStatementList(statements);
	}

	private CodegenAst codegenGroup(GroupExpression group) {
		return new JGroupExpression(codegenExpr(group.expr));
	}

	private CodegenAst codegenIdentifier(Identifier id) {
		if (id.sum) {
			return new JObjInstance(id.value.text);
		}
		return new JIdentifier(id.value.text);
	}

	private CodegenAst codegenNumber(Number number) {
		return new NumberLiteral(Double.parseDouble(number.value.text));
	}

	private CodegenAst codegenClosure(RClosure func) {
		var arg = func.args.isEmpty() ? null : func.args.getFirst();
		var body = codegenExpr(func.body);

		var closure = new JClosure(arg.name.text, body);
		closure.no_return = func.no_return;
		return closure;
	}

	private CodegenAst codegenBinop(BinOp binop) {
		var lhs = codegenExpr(binop.lhs);
		var rhs = codegenExpr(binop.rhs);
		if (binop.op.kind == TokenKind.ADDITIVE_OPERATOR || binop.op.kind == TokenKind.MULTPLICATIVE_OPERATOR) {
			if (lhs instanceof NumberLiteral lt && rhs instanceof NumberLiteral rt) {
				return constantFolding(binop.op.text, lt, rt);
			}
		}
		return new JBinOp(binop.op.text, lhs, rhs);
	}

	private CodegenAst constantFolding(String op, NumberLiteral lt, NumberLiteral rt) {
		double result = 0;
		switch (op) {
			case "+":
				result = lt.value + rt.value;
				break;
			case "-":
				result = lt.value - rt.value;
				break;
			case "*":
				result = lt.value * rt.value;
				break;
			case "/":
				result = lt.value / rt.value;
				break;
			case "%":
				result = lt.value % rt.value;
				break;
			default:
				It.unreachable();
		}
		return new NumberLiteral(result);
	}

}
