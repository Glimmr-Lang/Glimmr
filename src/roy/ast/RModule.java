package roy.ast;

import java.util.List;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class RModule implements Ast {
	public Token name;
	public List<Ast> decls;

	public RModule(Token name, List<Ast> decls) {
		this.name = name;
		this.decls = decls;
	}

	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (var decl: decls) {
			sb.append(decl.toString());
		}
		return sb.toString();
	}

}
