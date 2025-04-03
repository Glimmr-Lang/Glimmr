package roy.types;

/**
 *
 * @author hexaredecimal
 */
public class NumberType extends Type{

	@Override
	public String toString() {
		return "Number";
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof NumberType;
	}

	@Override
	public int hashCode() {
		return NumberType.class.hashCode();
	}

	@Override
	public Type clone() {
		return new NumberType();
	}
}
