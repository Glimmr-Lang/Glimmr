package roy.typechecker;

import roy.ast.Ast;
import roy.types.Type;

/**
 *
 * @author hexaredecimal
 */
public class CheckedNode {
	public Type type;
	public Ast node;

	public CheckedNode(Type type, Ast node) {
		this.type = type;
		this.node = node;
	}

	@Override
	public String toString() {
		return node + " : "+ type;
	}

	
}
