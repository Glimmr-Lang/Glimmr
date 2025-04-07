package roy.types;

import roy.tokens.Span;
import roy.tokens.Token;
import roy.tokens.TokenKind;

/**
 *
 * @author hexaredecimal
 */
public class NamedType extends Type{
	public Token name;

	public NamedType(Token name) {
		this.name = name;
	}


	@Override
	public String toString() {
		return name.text;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof NamedType)) return false;
		
		NamedType other = (NamedType) obj;
		return this.name.text.equals(other.name.text);
	}

	@Override
	public int hashCode() {
		return name.text.hashCode();
	}

	@Override
	public Type clone() {
		NamedType clone = new NamedType(name.clone());
		return clone;
	}
}
