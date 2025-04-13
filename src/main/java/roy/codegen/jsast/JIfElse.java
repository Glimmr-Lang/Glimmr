package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class JIfElse implements CodegenAst {
	public CodegenAst cond, then, elze;

	public JIfElse(CodegenAst cond, CodegenAst then, CodegenAst elze) {
		this.cond = cond;
		this.then = then;
		this.elze = elze;
	}

	@Override
	public String toString() {
		return String.format("(%s) ? %s : %s", cond, then, elze);
	}
}
