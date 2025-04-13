package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class JLetExpression implements CodegenAst {
	public String name;
	public CodegenAst value; 

	public JLetExpression(String name, CodegenAst value) {
		this.name = name;
		this.value = value;
	}


	@Override
	public String toString() {
		return String.format("var %s = %s;", name, value);
	}
}
