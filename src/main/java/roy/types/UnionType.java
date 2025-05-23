package roy.types;

import java.util.List;
import java.util.ArrayList;

/**
 *
 * @author hexaredecimal
 */
public class UnionType extends Type{
	public List<Type> types; 

	public UnionType(List<Type> types) {
		this.types = types;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < types.size(); i++) {
			sb.append(types.get(i));
			if (i < types.size() - 1) {
				sb.append(" | ");
			}
		}
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof UnionType;
	}

	@Override
	public int hashCode() {
		return UnionType.class.hashCode();
	}

	@Override
	public Type clone() {
		List<Type> clonedTypes = new ArrayList<>();
		for (Type type : types) {
			clonedTypes.add(type.clone());
		}
		return new UnionType(clonedTypes);
	}
}
