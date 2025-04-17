package roy.ast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class When implements Ast {
	public Ast match;
	public List<MatchCase> cases; 
	public Ast _else;

	public When(Ast match, List<MatchCase> patterns, Ast _else) {
		this.match = match;
		this.cases = patterns;
		this._else = _else;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb
			.append("when ")
			.append(match)
			.append(" { \n");
		
		for (var casse: cases) {
			sb.append(String.format("is %s ", casse).indent(4));
		}

		if (_else != null) {
			sb.append(String.format("else -> %s ", _else).indent(4));
		}
		sb.append("}");

		return sb.toString();
	}
}
