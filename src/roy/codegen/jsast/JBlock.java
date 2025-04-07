package roy.codegen.jsast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class JBlock implements CodegenAst {

	public List<CodegenAst> statements;

	public JBlock(List<CodegenAst> statements) {
		this.statements = statements;
	}

	@Override
	public String toString() {
		if (statements.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("(() => {").append("\n");
		CodegenAst last = null;
		if (!statements.isEmpty()) {
			last = statements.removeLast();
		}
		for (var stmt : statements) {
			sb.append(stmt.toString().indent(4));
		}

		if (last != null) {
			if (last instanceof JStatementList stmts) {
				last = stmts.statements.removeLast();
				sb.append(stmts);
				sb.append(String.format("return %s;", last).indent(4));
			} else {
				sb.append(String.format("return %s;", last).indent(4));
			}
		}
		sb.append("})()");
		return sb.toString();
	}
}
