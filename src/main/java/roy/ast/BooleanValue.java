package roy.ast;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class BooleanValue implements Ast {
	public Token value;

	public BooleanValue(Token value) {
		this.value = value;
	}

	
	@Override
	public String toString() {
		return value.text;
	}

}
