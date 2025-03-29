package roy.ast;

/**
 *
 * @author hexaredecimal
 */
public class BindPattern extends Pattern {
	public Identifier name; 

	public BindPattern(Identifier name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name.toString();
	}
}
