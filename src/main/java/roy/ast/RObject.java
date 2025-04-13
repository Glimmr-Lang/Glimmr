package roy.ast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class RObject implements Ast {
	public HashMap<Token, Ast> obj;

	public RObject(HashMap<Token, Ast> obj) {
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
			var kv = (Map.Entry<Token, Ast>)entries[i];
			sb.append(String.format("%s : %s", kv.getKey().text, kv.getValue()).indent(4).replaceAll("\n", ""));

			if (i < size -1) sb.append(", \n");
			else sb.append("\n");
		}

		sb.append("}");
		return sb.toString();
	}
	
}
