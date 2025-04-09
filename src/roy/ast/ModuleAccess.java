package roy.ast;

/**
 *
 * @author hexaredecimal
 */
public class ModuleAccess implements Ast {
	public Ast module;
	public Identifier field;

	public ModuleAccess(Ast obj, Identifier field) {
		this.module = obj;
		this.field = field;
	}
	
	@Override
	public String toString() {
		return module.toString() + "::" + field.toString();
	}

}
