package roy.ast;

/**
 *
 * @author hexaredecimal
 */
public class ExpressionPattern extends Pattern {
	public Ast expr;

	public ExpressionPattern(Ast expr) {
		this.expr = expr;
	}

	@Override
	public String toString() {
		return expr.toString();
	}
}
