package roy.ast;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class IfElse implements Ast {
	public Ast cond, then, elze;

	public IfElse(Ast cond, Ast then, Ast elze) {
		this.cond = cond;
		this.then = then;
		this.elze = elze;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
			.append("\n")
			.append("if ".indent(4).replaceAll("\n", ""))
			.append(cond)
			.append(" then")
			.append("\n")
			.append(then.toString().indent(8))
			.append("else ".indent(4).replaceAll("\n", ""))
			.append(elze);

		return sb.toString();
	}

}
