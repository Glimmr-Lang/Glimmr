package roy.ast;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class Number implements Ast {
	private Token value;

	public Number(Token value) {
		this.value = value;
	}

	public Token getValue() {
		return value;
	}
	
	@Override
	public String toString() {
		return value.text;
	}

}
