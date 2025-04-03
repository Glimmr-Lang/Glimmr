package roy.types;

import java.util.HashMap;
import roy.rt.It;

/**
 *
 * @author hexaredecimal
 */
public class Type {
	private static HashMap<String, Type> TYPES = new HashMap<>();
	
	static {
		TYPES.put("String", new StringType());
		TYPES.put("Number", new NumberType());
		TYPES.put("Boolean", new BooleanType());
		TYPES.put("Unit", new UnitType());
	}

	public static boolean isPrimitive(String name) {
		return TYPES.containsKey(name);
	}

	public static Type toPrimitive(String text) {
		if (isPrimitive(text)) return TYPES.get(text);
		It.unreachable();
		return null;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		// Base implementation just checks for same class
		// Subclasses will override with more specific behavior
		return obj != null && getClass() == obj.getClass();
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}
