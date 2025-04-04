package roy.ast;

import java.util.List;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class AnnotatedFunction implements Ast {
	private List<String> annotations; 
	public RFunction func; 
	public boolean isExtern;

	public AnnotatedFunction(List<String> annotations, RFunction func) {
		this.annotations = annotations;
		this.func = func;
		this.isExtern = annotations.contains("extern");
	}
	
	
	@Override
	public String toString() {
		return func.toString();
	}

}
