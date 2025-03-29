package roy.types;

/**
 *
 * @author hexaredecimal
 */
public class ListType extends Type{
	public Type inner;

	public ListType(Type inner) {
		this.inner = inner;
	}

	
	@Override
	public String toString() {
		return "[" + inner + "]";
	}
	
}
