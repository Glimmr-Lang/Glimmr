package roy.ast;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class Identifier implements Ast {
	public Token value;
	public boolean sum;

	public Identifier(Token value) {
		this.value = value;
		this.sum = false;
	}

	
	@Override
	public String toString() {
		return value.text;
	}

}
