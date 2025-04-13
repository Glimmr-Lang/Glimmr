package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class JFieldAccess implements CodegenAst {
	public CodegenAst expr;
	public String field; 

	public JFieldAccess(CodegenAst expr, String field) {
		this.expr = expr;
		this.field = field;
	}


	@Override
	public String toString() {
		return String.format("%s.%s", expr, field);
	}
}
