package roy.ast;

import java.util.List;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class Tuple implements Ast {
	public List<Ast> values;

	public Tuple(List<Ast> values) {
		this.values = values;
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		for (int i = 0; i < values.size(); i++) {
			var top = values.get(i);
			sb.append(top);

			if (i < values.size() - 1) {
				sb.append(", ");
			}
		}

		sb.append(")");
		return sb.toString();
	}

}
