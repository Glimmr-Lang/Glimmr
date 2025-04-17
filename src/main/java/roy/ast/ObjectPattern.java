package roy.ast;

import java.util.HashMap;
import java.util.List;

/**
 *
 * @author hexaredecimal
 */
public class ObjectPattern extends Pattern {
	public HashMap<Identifier, Ast> object;

	public ObjectPattern(HashMap<Identifier, Ast> object) {
		this.object = object;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		var ee = object.entrySet();
		sb.append("{");
		int end = ee.size();
		int index = 0;
		for (var kv: ee) {
			sb.append(kv.getKey()).append(": ").append(kv.getValue());
			if (index < end -1) {
				sb.append(", ");
			}
			index ++;
		}
		sb.append("}");

		return sb.toString();
	}
}
