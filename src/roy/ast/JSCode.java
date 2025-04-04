package roy.ast;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class JSCode implements Ast {
	public Token start, end;
	public String code;

	public JSCode(Token start, Token end, String code) {
		this.start = start;
		this.end = end;
		this.code = code;
	}

	
	@Override
	public String toString() {
		return code; 
	}
}
