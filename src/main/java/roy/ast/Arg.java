package roy.ast;

import roy.tokens.Token;
import roy.types.Type;

/**
 *
 * @author hexaredecimal
 */
public class Arg implements Ast {
	public Token name;
	public Type type;

	public Arg(Token token, Type type) {
		this.name = token;
		this.type = type;
	}

	@Override
	public String toString() {
		return name.text + ":" + type;
	}
}
