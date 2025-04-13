package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class JBinOp implements CodegenAst {
	private String op;
	private CodegenAst lhs, rhs;

	public JBinOp(String op, CodegenAst lhs, CodegenAst rhs) {
		this.op = op.equals("++") ? "+" : op;
		this.lhs = lhs;
		this.rhs = rhs;
	}

	@Override
	public String toString() {
		return String.format("%s %s %s", lhs, op, rhs);
	}
}
