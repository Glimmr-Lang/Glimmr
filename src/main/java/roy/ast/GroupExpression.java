package roy.ast;


/**
 *
 * @author hexaredecimal
 */
public class GroupExpression implements Ast {
	public Ast expr;

	public GroupExpression(Ast expr) {
		this.expr = expr;
	}

	
	@Override
	public String toString() {
		return expr.toString();
	}

}
