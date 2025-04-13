package roy.ast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class ConsPattern extends Pattern {
	public Ast name;
	public List<Pattern> exprs;

	public ConsPattern(Ast name, List<Pattern> exprs) {
		this.name = name;
		this.exprs = exprs;
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb
			.append(name)
			.append("(");
		var size = exprs.size();
		for (int i = 0; i < size; i++) {
			var top = exprs.get(i);
			sb.append(top);

			if (i < size - 1) {
				sb.append(", ");
			}
		}

		sb.append(")");
		return sb.toString();
	}
}
