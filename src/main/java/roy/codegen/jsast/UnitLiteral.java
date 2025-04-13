package roy.codegen.jsast;

/**
 *
 * @author hexaredecimal
 */
public class UnitLiteral implements CodegenAst {


	@Override
	public String toString() {
		// Unit is an object because it can be assignable to variable and passed as an argument. 
		// The typechecker prevents the collusion between an Object and Unit.
		return "new Unit() /* Unit */ ";
	}
}
