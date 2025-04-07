package roy.codegen.jsast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class JCall implements CodegenAst {

	public CodegenAst expr;
	public List<CodegenAst> params;

	public JCall(CodegenAst expr, List<CodegenAst> params) {
		this.expr = expr;
		this.params = params;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb
			.append(expr.toString())
			.append(" ");
		for (var stmt : params) {
			sb
				.append("(")
				.append(stmt.toString())
				.append(") ");
		}
		return sb.toString().trim();
	}
}
