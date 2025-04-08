package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class JIdentifier implements CodegenAst {
	private String value;

	public JIdentifier(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return String.format("%s", value);
	}
}
