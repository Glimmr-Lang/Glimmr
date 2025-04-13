package roy.ast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class Match implements Ast {
	public Ast match;
	public List<MatchCase> cases; 

	public Match(Ast match, List<MatchCase> patterns) {
		this.match = match;
		this.cases = patterns;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb
			.append("match ")
			.append(match)
			.append("\n");
		
		for (var casse: cases) {
			sb.append(String.format("| %s ", casse).indent(4));
		}

		return sb.toString();
	}
}
