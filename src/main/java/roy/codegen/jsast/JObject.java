package roy.codegen.jsast;

import java.util.HashMap;
import java.util.Map;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class JObject implements CodegenAst {
	public HashMap<String, CodegenAst> obj;

	public JObject(HashMap<String, CodegenAst> obj) {
		this.obj = obj;
	}

	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder()
			.append("{")
			.append("\n");

		if (obj.isEmpty()) return "{}";

		var entries = obj.entrySet().toArray();
		var size = entries.length;
		for (int i = 0; i < size; i++) {
			var kv = (Map.Entry<String, CodegenAst>)entries[i];
			sb.append(String.format("%s : %s", kv.getKey(), kv.getValue()).indent(4).replaceAll("\n", ""));

			if (i < size -1) sb.append(", \n");
			else sb.append("\n");
		}

		sb.append("}");
		return sb.toString();
	}
}
