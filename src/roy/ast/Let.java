package roy.ast;

import roy.tokens.Token;
import roy.types.Type;

/**
 *
 * @author hexaredecimal
 */
public class Let implements Ast {
	public Token name;
	public Type type; 
	public Ast expr; 

	public Let(Token name, Type type, Ast expr) {
		this.name = name;
		this.type = type;
		this.expr = expr;
	}


	@Override
	public String toString() {
		return String.format("%s: %s = %s", name.text, type, expr);
	}

}
