package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class StringLiteral implements CodegenAst {
	private String value;

	public StringLiteral(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return String.format("'%s'", value);
	}
}
