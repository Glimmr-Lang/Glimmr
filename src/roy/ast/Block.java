package roy.ast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class Block implements Ast {
	public List<Ast> exprs;

	public Block(List<Ast> exprs) {
		this.exprs = exprs;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
			.append("{")
			.append("\n");

		if (exprs.isEmpty()) return "{}";

		var last = exprs.removeLast();
		for (var expr: exprs) {
			sb.append(expr.toString().indent(4));
		}
		sb.append(String.format("return %s", last).indent(4));
		sb.append("}");
		return sb.toString();
	}
		
}
