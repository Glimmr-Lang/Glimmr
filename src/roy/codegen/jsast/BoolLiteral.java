package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class BoolLiteral implements CodegenAst {
	private boolean value;

	public BoolLiteral(boolean value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return String.format("%s", value ? "true" : "false");
	}
}
