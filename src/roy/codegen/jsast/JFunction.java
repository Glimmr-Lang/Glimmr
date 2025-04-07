package roy.codegen.jsast;

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
			
			if (arg != null) sb.append(arg.toString());
			
			sb.append(") {")
			.append("\n");
			
			if (body instanceof JStringCodeEmbed) {
				sb.append(String.format(" %s", body).toString().indent(4));
			} else {
				sb.append(String.format("return %s;", body).toString().indent(4));
			}
			
			sb.append("}");
		return sb.toString();
	}

}
