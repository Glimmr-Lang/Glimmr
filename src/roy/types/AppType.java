package roy.types;

import java.util.List;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class AppType extends Type{
	public Token cons;
	public List<Type> args; 

	public AppType(Token name, List<Type> args) {
		this.cons = name;
		this.args = args;
	}
	
	
	@Override
	public String toString() {
		var f = args.toString().indexOf("[");
		var l = args.toString().lastIndexOf("]");
		return cons.text + " " + args.toString().substring(f+1, l);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof AppType)) return false;
		
		AppType other = (AppType) obj;
		return this.cons.text.equals(other.cons.text) && this.args.equals(other.args);
	}
	
	@Override
	public int hashCode() {
		return 31 * cons.hashCode() + args.hashCode();
	}
}
