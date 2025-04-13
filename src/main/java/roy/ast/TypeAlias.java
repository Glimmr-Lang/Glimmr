package roy.ast;

import roy.tokens.Token;
import roy.types.Type;

/**
 *
 * @author hexaredecimal
 */
public class TypeAlias implements Ast {
	public Token name;
	public Type type;

	public TypeAlias(Token name, Type type) {
		this.name = name;
		this.type = type;
	}
	
	
	@Override
	public String toString() {
		return "type " + name.text + " = " + type;
	}

}
