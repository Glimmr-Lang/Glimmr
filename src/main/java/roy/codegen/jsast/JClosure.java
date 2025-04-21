package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class JClosure implements CodegenAst {

	public String arg;
	public CodegenAst body;
	public boolean no_return;

	public JClosure(String arg, CodegenAst body) {
		this.arg = arg;
		this.body = body;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb
			.append("function (")
			.append(arg)
			.append(") {")
			.append("\n");
		if (no_return) {
			sb.append(body.toString().indent(4));
		} else {
			if (body instanceof JStringCodeEmbed) {
				sb.append(String.format("return %s", body).indent(4));
			} else if (body instanceof JStatementList jsl) {
				sb.append(renderStatementList(jsl));
			}else {
				sb.append(String.format("return %s;", body).indent(4));
			}
		}
		sb.append("}");
		return sb.toString();
	}

	private String renderStatementList(JStatementList jsl) {
		var sb = new StringBuilder();
		var last = jsl.statements.removeLast();
		for (var stmt : jsl.statements) {
			sb.append(stmt.toString().indent(4));
		}

		if (last instanceof JStatementList jsl2) {
			sb.append("\n").append(renderStatementList(jsl2));
		} else {
			sb.append(String.format("return %s", last).indent(4));
		}
		return sb.toString();
	}
}
