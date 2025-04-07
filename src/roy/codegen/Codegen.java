package roy.codegen;

import java.lang.invoke.CallSite;
import java.util.ArrayList;
import java.util.List;
import roy.ast.AnnotatedFunction;
import roy.ast.Ast;
import roy.ast.BinOp;
import roy.ast.Block;
import roy.ast.Call;
import roy.ast.GroupExpression;
import roy.ast.Identifier;
import roy.ast.IfElse;
import roy.ast.JSCode;
import roy.ast.LetIn;
import roy.ast.Number;
import roy.ast.RClosure;
import roy.ast.RFunction;
import roy.ast.RString;
import roy.ast.Unit;
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
import roy.codegen.jsast.JStringCodeEmbed;
import roy.codegen.jsast.NumberLiteral;
import roy.codegen.jsast.StringLiteral;
import roy.codegen.jsast.UnitLiteral;
import roy.rt.It;
import roy.tokens.TokenKind;

public class Codegen {

	private List<Ast> nodes;

	public Codegen(List<Ast> nodes) {
		this.nodes = nodes;
	}

	public void gen() {
		for (var node : nodes) {
			if (node instanceof RFunction func) {
				codegenFunction(func);
			}

			if (node instanceof AnnotatedFunction afunc) {
				codegenAnnotatedFunction(afunc);
			}

		}
	}
	
	private void codegenAnnotatedFunction(AnnotatedFunction afunc) {
		var func = afunc.func;
		var name = func.name.text;
		var arg = func.args.isEmpty() ? null : func.args.getFirst();
		var body = codegenExpr(func.body);

		var arg_name = arg == null ? "" : arg.name.text;
		var fx = new JFunction(name, arg_name, body);
		fx.export = afunc.isExport;
		System.out.println("" + fx);
	}

	private void codegenFunction(RFunction func) {
		var name = func.name.text;
		var arg = func.args.isEmpty() ? null : func.args.getFirst();
		var body = codegenExpr(func.body);

		var arg_name = arg == null ? "" : arg.name.text;
		var fx = new JFunction(name, arg_name, body);
		System.out.println("" + fx);
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

		It.todo(expr.getClass().getName());
		return null;
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
		for (var param: call.params) {
			params.add(codegenExpr(param));
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
