package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class JStringCodeEmbed implements CodegenAst {
	public String code;

	public JStringCodeEmbed(String code) {
		this.code = code;
	}
	
	@Override
	public String toString() {
		return code;
	}
}
