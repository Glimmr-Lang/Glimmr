package roy.codegen.jsast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class JStatementList implements CodegenAst {
	public List<CodegenAst> statements;

	public JStatementList(List<CodegenAst> statements) {
		this.statements = statements;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (var stmt: statements) {
			sb.append(stmt.toString().indent(4));
		}
		return sb.toString();
	}
}
