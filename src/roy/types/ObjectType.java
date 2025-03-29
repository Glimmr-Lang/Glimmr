package roy.types;

import java.util.HashMap;
import java.util.Map;
import roy.ast.Ast;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class ObjectType extends Type {
	public HashMap<Token, Type> fields;

	public ObjectType(HashMap<Token, Type> obj) {
		this.fields = obj;
	}


	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder()
			.append("{ ");

		if (fields.isEmpty()) {
			return "{}";
		}

		var entries = fields.entrySet().toArray();
		var size = entries.length;
		for (int i = 0; i < size; i++) {
			var kv = (Map.Entry<Token, Ast>) entries[i];
			sb.append(String.format("%s : %s", kv.getKey().text, kv.getValue()));
			if (i < size - 1) {
				sb.append(", ");
			} 
		}

		sb.append(" }");
		return sb.toString();
	}

}
