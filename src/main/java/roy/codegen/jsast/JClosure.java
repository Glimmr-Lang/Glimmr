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
			.append(arg.toString())
			.append(") {")
			.append("\n");
		if (no_return) {
			sb.append(body.toString().indent(4));
		} else {
			if (body instanceof JStringCodeEmbed) {
				sb.append(String.format("return %s", body).toString().indent(4));
			} else {
				sb.append(String.format("return %s;", body).toString().indent(4));
			}
		}
		sb.append("}");
		return sb.toString();
	}

}
