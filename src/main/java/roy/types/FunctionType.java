package roy.types;

import java.util.ArrayList;
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
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof FunctionType)) return false;
		
		FunctionType other = (FunctionType) obj;
		if (this.args.size() != other.args.size()) return false;
		
		// Check all argument types
		for (int i = 0; i < this.args.size(); i++) {
			if (!this.args.get(i).equals(other.args.get(i))) {
				return false;
			}
		}
		
		// Check return type
		return this.type.equals(other.type);
	}

	@Override
	public int hashCode() {
		int result = 17;
		for (Type arg : args) {
			result = 31 * result + arg.hashCode();
		}
		result = 31 * result + type.hashCode();
		return result;
	}

	@Override
	public Type clone() {
		List<Type> clonedArgs = new ArrayList<>();
		for (Type arg : args) {
			clonedArgs.add(arg.clone());
		}
		return new FunctionType(clonedArgs, type.clone());
	}
}
