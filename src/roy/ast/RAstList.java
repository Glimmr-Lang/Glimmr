package roy.ast;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class RAstList implements Ast {
	public List<Ast> decls;

	public RAstList(List<Ast> decls) {
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
