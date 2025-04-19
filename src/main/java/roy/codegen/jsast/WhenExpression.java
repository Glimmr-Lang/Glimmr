package roy.codegen.jsast;

import java.util.List;
import roy.ast.Ast;
import roy.ast.ExpressionPattern;
import roy.ast.RString;
import roy.rt.It;

/**
 *
 * @author hexaredecimal
 */
public class WhenExpression implements CodegenAst {

	private static int count = 0;
	public CodegenAst match;
	public List<WhenCases> List;
	public CodegenAst elze;

	public WhenExpression(CodegenAst match, List<WhenCases> List, CodegenAst elze) {
		this.match = match;
		this.List = List;
		this.elze = elze;
	}

	@Override
	public String toString() {
		var name = String.format("%s", allocateMatchName());
		var cond = String.format("let %s = %s;", name, match);
		StringBuilder sb = new StringBuilder();
		sb
			.append("(() => {\n")
			.append(cond.indent(4));
		for (var _case : List) {
			var pattern = _case.cond;
			if (pattern instanceof ExpressionPattern expr_pattern) {
				var bd = createBodyForExprPattern(name, expr_pattern.expr, _case.body);
				sb.append(bd.indent(4));
			}
		}

		if (elze != null) {
			sb.append(genElse(elze).indent(4));
		}

		sb.append("\n");
		sb.append("}) ();");
		return sb.toString();
	}

	private String createBodyForExprPattern(String cond, Ast pattern, CodegenAst body) {
		if (pattern instanceof roy.ast.Number number) {
			return genNumberPattern(cond, number, body);
		}
		if (pattern instanceof RString str) {
			return genStringPattern(cond, str, body);
		}

		It.todo();
		return null;
	}

	private String genNumberPattern(String cond, roy.ast.Number number, CodegenAst body) {
		StringBuilder sb = new StringBuilder();
		var c = String.format("if (%s == %s) {\n", cond, number.value.text);
		sb.append(c);
		c = String.format("return %s;", body.toString());
		sb.append(c.indent(4));
		sb.append("}\n");
		return sb.toString();
	}

	private String genStringPattern(String cond, RString str, CodegenAst body) {
		StringBuilder sb = new StringBuilder();
		var c = String.format("if (%s == \"%s\") {\n", cond, str.value.text);
		sb.append(c);
		c = String.format("return %s;", body.toString());
		sb.append(c.indent(4));
		sb.append("}\n");
		return sb.toString();
	}

	private String genElse(CodegenAst body) {
		StringBuilder sb = new StringBuilder();
		var c = String.format("return %s;", body.toString());
		sb.append(c).append("\n");
		return sb.toString();
	}

	private String allocateMatchName() {
		return String.format("_match_%d_", count);
	}

}
