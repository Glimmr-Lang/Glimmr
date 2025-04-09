package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class JFunctionObject implements CodegenAst {
	public String name; 
	public int arity;

	public JFunctionObject(String name, int arity) {
		this.name = name;
		this.arity = arity;
	}

	@Override
	public String toString() {
		if (arity == 0) {
			return genZeroFuncClass();
		}
		return genFuncClass();
	}

	private String genFuncClass() {
		StringBuilder sb = new StringBuilder();
		sb
			.append("function ")
			.append(name)
			.append("(")
			.append(genArgsHeader())
			.append(") {\n");

		int i = arity; 
		while (i >= 1) {
			sb.append(genIf(i).indent(4));
			i--;
		}
		
		sb.append("}");
		return sb.toString();
	}

	private String genArgsHeader() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < arity; i++) {
			var name = String.format("f%d", i);
			sb.append(name);
			if (i < arity - 1) {
				sb.append(", ");
			}
		}

		return sb.toString();
	} 

	private String genIf(int count) {
		StringBuilder sb = new StringBuilder();
		sb
			.append("if ")
			.append("(")
			.append(genCheck(count))
			.append(") {\n")
			.append(genBody(count).indent(4))
			.append("}");
		return sb.toString();
	}

	private String genCheck(int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			var name = String.format("f%d != undefined", i);
			sb.append(name);
			if (i < count - 1) {
				sb.append(" && ");
			}
		}
		return sb.toString();
	} 

	private String genBody(int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			var name = String.format("this.f%d = f%d; ", i, i);
			sb.append(name).append("\n");
		}
		return sb.toString();
	} 

	
	private String genZeroFuncClass() {
		StringBuilder sb = new StringBuilder();
		sb
			.append("function ")
			.append(name)
			.append("() {}")
			.append("\n");
		return sb.toString();
	}
}
