package roy.ast;

import java.util.List;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class LetIn implements Ast {
	public List<Let> lets; 
	public Ast in;

	public LetIn(List<Let> lets, Ast in) {
		this.lets = lets;
		this.in = in;
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
		.append("\n")
		.append("let ".indent(4).replaceAll("\n", ""));
		
		if (lets.size() > 1) {
			sb.append("\n");
			for (var let: lets) {
				sb.append(let.toString().indent(8));
			}
			sb
				.append(" in ".indent(4))
				.append(in.toString().indent(8));
		} else {
			sb
				.append(lets.getFirst())
				.append(" in ")
				.append(((Let)lets.getFirst()).name.text);
		}
		return sb.toString();
	}

}
