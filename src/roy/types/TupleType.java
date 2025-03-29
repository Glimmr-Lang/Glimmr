package roy.types;

import java.util.List;

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
	
}
