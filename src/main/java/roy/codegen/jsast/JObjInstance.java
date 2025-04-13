package roy.codegen.jsast;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class JObjInstance implements CodegenAst {

	public String name;
	public List<CodegenAst> params;

	public JObjInstance(String name, List<CodegenAst> params) {
		this.name = name;
		this.params = params;
	}

	public JObjInstance(String name) {
		this.name = name;
		this.params = new ArrayList<>();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb
			.append("new ")
			.append(name);

		if (params.isEmpty()) {
			sb.append("()");
			return sb.toString().trim();
		}

		int size = params.size();
		sb.append(" (");
		for (int i = 0; i < size; i++) {
			var stmt = params.get(i);
			sb.append(stmt.toString());
			if (i < size - 1) {
				sb.append(", ");
			}
		}
		sb.append(") ");
		return sb.toString().trim();
	}
}
