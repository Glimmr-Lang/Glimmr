package roy.errors;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class ErrorNode {
	public Token token;
	public String message;

	public ErrorNode(Token token, String message) {
		this.token = token;
		this.message = message;
	}

}
