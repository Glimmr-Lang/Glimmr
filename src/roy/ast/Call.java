package roy.ast;

import java.util.List;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class Call implements Ast {
	public Ast expr; 
	public List<Ast> params;

	public Call(Ast expr, List<Ast> params) {
		this.expr = expr;
		this.params = params;
	}

	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(expr);
		for (var param: params) {
			sb.append(" ").append(param);
		}
		return sb.toString();
	}
}
