package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class JGroupExpression implements CodegenAst {
	public CodegenAst expr;

	public JGroupExpression(CodegenAst expr) {
		this.expr = expr;
	}
	
	@Override
	public String toString() {
		return String.format("(%s)", expr);
	}
}
