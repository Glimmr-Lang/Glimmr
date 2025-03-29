package roy.types;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class AppType extends Type{
	public Type cons;
	public List<Type> args; 

	public AppType(Type name, List<Type> args) {
		this.cons = name;
		this.args = args;
	}
	
	
	@Override
	public String toString() {
		return cons + " " + args;
	}
	
}
