package roy.types;

import roy.tokens.Span;
import roy.tokens.Token;
import roy.tokens.TokenKind;

/**
 *
 * @author hexaredecimal
 */
public class TypeVariable extends Type{
	private static int count = 0, dup = 1;
	
	public Token name;
	public boolean is_user_defined;

	public TypeVariable(Token text) {
		name = text.clone();
		is_user_defined = false;
		name.text += findName();
	}

	public TypeVariable(Token text, boolean is_def) {
		name = text;
		is_user_defined = is_def;
		findName();
	}

	public TypeVariable(Span span) {
		name = new Token(TokenKind.ID, null);
		name.span = span;
		name.text = findName();
	}

	public static void reset() {
		count = 0;
		dup = 1;
	}

	private String findName() {
		char[] chars = "abcdefghijklmnopqrstuvxyz".toCharArray();
		var text = ("" + chars[count++]).repeat(dup);
		if (count >= chars.length) {
			count = 0;
			dup++;
		}
		return text;
	}

	@Override
	public String toString() {
		return is_user_defined ? name.text : "'" + name.text;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof TypeVariable)) return false;
		
		TypeVariable other = (TypeVariable) obj;
		return this.name.text.equals(other.name.text);
	}

	@Override
	public int hashCode() {
		return name.text.hashCode();
	}

	@Override
	public Type clone() {
		TypeVariable clone = new TypeVariable(name);
		clone.is_user_defined = this.is_user_defined;
		return clone;
	}
}
