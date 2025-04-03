package roy.types;

/**
 *
 * @author hexaredecimal
 */
public class BooleanType extends Type{

	@Override
	public String toString() {
		return "Boolean";
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof BooleanType;
	}

	@Override
	public int hashCode() {
		return BooleanType.class.hashCode();
	}
	
	@Override
	public Type clone() {
		return new BooleanType();
	}
	
}
