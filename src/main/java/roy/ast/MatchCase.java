package roy.ast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class MatchCase implements Ast {
	public Pattern pattern;
	public Ast body;

	public MatchCase(Pattern pattern, Ast body) {
		this.pattern = pattern;
		this.body = body;
	}

	@Override
	public String toString() {
		return pattern + " -> " + body;
	}
}
