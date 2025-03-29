package roy.ast;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class RString implements Ast {
	private Token value;

	public RString(Token value) {
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
