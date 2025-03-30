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
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof ListType)) return false;
		
		ListType other = (ListType) obj;
		return this.inner.equals(other.inner);
	}

	@Override
	public int hashCode() {
		return 31 * ListType.class.hashCode() + inner.hashCode();
	}
}
