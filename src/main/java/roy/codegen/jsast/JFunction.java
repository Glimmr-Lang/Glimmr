package roy.codegen.jsast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class JFunction implements CodegenAst {

	public String name, arg;
	public CodegenAst body;
	public boolean export;

	public JFunction(String name, String arg, CodegenAst body) {
		this.name = name;
		this.arg = arg;
		this.body = body;
		this.export = false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		if (export) {
			sb.append("export ");
		}

		sb
						.append("function ")
						.append(name)
						.append("(");

		if (arg != null) {
			sb.append(arg);
		}

		sb.append(") {")
						.append("\n");

		if (body instanceof JStringCodeEmbed) {
			sb.append(String.format(" %s", body).indent(4));
		} else if (body instanceof JStatementList jsl) {
			sb.append(renderStatementList(jsl));
		} else if (body instanceof JBlock block) {
			var last = block.statements.removeLast();
			for (var stmt : block.statements) {
				var s = stmt.toString();
				sb.append(s.indent(4));
				if (!s.endsWith(";")) {
					sb.append(";");
				}
			}
			sb.append(String.format("return %s", last).indent(4));
		} else if (body instanceof JLetExpression lt) {
			var stmts = List.of((CodegenAst) lt);
			var li = new JStatementList(stmts);
			sb.append(li);
		} else {
			sb.append(String.format("return %s;", body).indent(4));
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
