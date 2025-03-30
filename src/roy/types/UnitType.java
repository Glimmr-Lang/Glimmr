package roy.types;

/**
 *
 * @author hexaredecimal
 */
public class UnitType extends Type{

	@Override
	public String toString() {
		return "Unit";
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof UnitType;
	}

	@Override
	public int hashCode() {
		return UnitType.class.hashCode();
	}
	
}
