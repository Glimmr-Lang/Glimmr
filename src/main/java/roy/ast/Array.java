package roy.ast;

import java.util.List;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class Array implements Ast {
	public List<Ast> elements;
	public Token start;
	
	public Array(List<Ast> elements, Token start) {
		this.elements = elements;
		this.start = start;
	}
	

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		var size = elements.size();
		sb.append("[");
		for (int i = 0; i < size; i++) {
			var top = elements.get(i);
			sb.append(top);
			if (i < size - 1) {
				sb.append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}

}
