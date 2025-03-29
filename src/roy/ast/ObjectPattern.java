package roy.ast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class ObjectPattern extends Pattern {
	public List<Pattern> exprs;

	public ObjectPattern(List<Pattern> exprs) {
		this.exprs = exprs;
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		var size = exprs.size();
		sb.append("{");
		for (int i = 0; i < size; i++) {
			var top = exprs.get(i);
			sb.append(top);

			if (i < size - 1) {
				sb.append(", ");
			}
		}
		sb.append("}");

		return sb.toString();
	}
}
