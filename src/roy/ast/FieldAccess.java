package roy.ast;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class Number implements Ast {
	public Token value;

	public Number(Token value) {
		this.value = value;
	}

	
	@Override
	public String toString() {
		return value.text;
	}

}
