package roy.codegen.jsast;

import roy.ast.Ast;

/**
 *
 * @author hexaredecimal
 */
public class WhenCases implements CodegenAst {
	public Ast cond;
	public CodegenAst body;

	public WhenCases(Ast cond, CodegenAst body) {
		this.cond = cond;
		this.body = body;
	}
}
