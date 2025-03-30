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

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof ObjectType)) return false;
		
		ObjectType other = (ObjectType) obj;
		if (this.fields.size() != other.fields.size()) return false;
		
		// Check that all fields have the same type
		for (Map.Entry<Token, Type> entry : this.fields.entrySet()) {
			String fieldName = entry.getKey().text;
			if (!other.fields.containsKey(fieldName)) {
				return false;
			}
			if (!entry.getValue().equals(other.fields.get(fieldName))) {
				return false;
			}
		}
		
		return true;
	}

	@Override
	public int hashCode() {
		int result = 17;
		for (Map.Entry<Token, Type> entry : fields.entrySet()) {
			result = 31 * result + entry.getKey().hashCode();
			result = 31 * result + entry.getValue().hashCode();
		}
		return result;
	}

}
