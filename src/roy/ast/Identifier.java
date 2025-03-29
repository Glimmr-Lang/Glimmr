package roy.ast;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class Identifier implements Ast {
	public Token value;

	public Identifier(Token value) {
		this.value = value;
	}

	
	@Override
	public String toString() {
		return value.text;
	}

}
