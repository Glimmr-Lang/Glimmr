package roy.ast;

/**
 *
 * @author hexaredecimal
 */
public class FieldAccess implements Ast {
	
	public Ast obj;
	public Identifier field;

	public FieldAccess(Ast obj, Identifier field) {
		this.obj = obj;
		this.field = field;
	}
	
	@Override
	public String toString() {
		return obj.toString() + "." + field.toString();
	}

}
