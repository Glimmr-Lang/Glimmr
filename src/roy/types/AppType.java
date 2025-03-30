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
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof AppType)) return false;
		
		AppType other = (AppType) obj;
		return this.cons.equals(other.cons) && this.args.equals(other.args);
	}
	
	@Override
	public int hashCode() {
		return 31 * cons.hashCode() + args.hashCode();
	}
}
