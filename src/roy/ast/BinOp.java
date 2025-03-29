package roy.ast;

import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class BinOp implements Ast {
	public Ast lhs, rhs;
	public Token op;

	public BinOp(Ast lhs, Ast rhs, Token op) {
		this.lhs = lhs;
		this.rhs = rhs;
		this.op = op;
	}

	
	@Override
	public String toString() {
		return String.format("(%s %s %s)", lhs, op.text, rhs);
	}
}
