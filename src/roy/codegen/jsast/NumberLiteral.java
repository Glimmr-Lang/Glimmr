package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class NumberLiteral implements CodegenAst {
	public double value;

	public NumberLiteral(double value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return String.format("%s", String.valueOf(value));
	}
}
