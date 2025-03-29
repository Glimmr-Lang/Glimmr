package roy.types;

import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class FunctionType extends Type{
	public List<Type> args;
	public Type type; 

	public FunctionType(List<Type> args, Type type) {
		this.args = args;
		this.type = type;
	}


	@Override
	public String toString() {
		var a = args.toString();
		a = a.substring(0, a.length() - 1);
		a = a.substring(1);
		return a + " -> " + type;
	}
	
}
