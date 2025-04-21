package roy.codegen.jsast;

import java.util.List;
import roy.codegen.jsast.CodegenAst;

/**
 *
 * @author hexaredecimal
 */
public class JArray implements CodegenAst {
	public List<CodegenAst> nodes;

	public JArray(List<CodegenAst> nodes) {
		this.nodes = nodes;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		var size = nodes.size();
		sb.append("[");
		for (int i = 0; i < size; i++) {
			var top = nodes.get(i);
			sb.append(top);
			if (i < size - 1) {
				sb.append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
}
