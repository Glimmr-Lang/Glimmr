package roy.ast;

import java.util.List;
import roy.tokens.Token;
import roy.types.Type;

/**
 *
 * @author hexaredecimal
 */
public class RClosure implements Ast {
	public Token name;
	public List<Arg> args; 
	public Type type;
	public Ast body;
	public boolean no_return;

	public RClosure(Token name, List<Arg> args, Type type, Ast body) {
		this.name = name;
		this.args = args;
		this.type = type;
		this.body = body;
		this.no_return = false;
	}

	@Override
	public String toString() {
		var a = args.toString();
		a = a.substring(0, a.length() - 1);
		a = a.substring(1);
		return String.format("{ %s: %s ->\n%s}", a.isEmpty() ? "()" : a , type, body.toString().indent(4));
	}
}
