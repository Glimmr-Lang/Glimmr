package roy.ast;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class RString implements Ast {
	public Token value;

	public RString(Token value) {
		this.value = value;
	}
	
	@Override
	public String toString() {
		return value.text;
	}


}
