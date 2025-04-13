package roy.types;

import java.util.List;
import java.util.ArrayList;

/**
 *
 * @author hexaredecimal
 */
public class TupleType extends Type{
	public List<Type> nodes; 

	public TupleType(List<Type> nodes) {
		this.nodes = nodes;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		for (int i = 0; i < nodes.size(); i++) {
			var top = nodes.get(i);
			sb.append(top);

			if (i < nodes.size() - 1) {
				sb.append(", ");
			}
		}

		sb.append(")");
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof TupleType)) return false;
		
		TupleType other = (TupleType) obj;
		if (this.nodes.size() != other.nodes.size()) return false;
		
		// Check all element types
		for (int i = 0; i < this.nodes.size(); i++) {
			if (!this.nodes.get(i).equals(other.nodes.get(i))) {
				return false;
			}
		}
		
		return true;
	}

	@Override
	public int hashCode() {
		int result = 17;
		for (Type type : nodes) {
			result = 31 * result + type.hashCode();
		}
		return result;
	}

	@Override
	public Type clone() {
		List<Type> clonedTypes = new ArrayList<>();
		for (Type type : nodes) {
			clonedTypes.add(type.clone());
		}
		return new TupleType(clonedTypes);
	}
}
