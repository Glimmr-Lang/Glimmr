package roy.types;

/**
 *
 * @author hexaredecimal
 */
public class StringType extends Type{

	@Override
	public String toString() {
		return "String";
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof StringType;
	}

	@Override
	public int hashCode() {
		return StringType.class.hashCode();
	}

	@Override
	public Type clone() {
		return new StringType();
	}
}
