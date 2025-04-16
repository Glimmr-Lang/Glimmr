package org.lexer;

/**
 *
 * @author hexaredecimal
 */
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.TokenId;

public class GlimmrTokenId implements TokenId {

	private final String name;
	private final String primaryCategory;
	private final int id;

	GlimmrTokenId(String name,String primaryCategory,int id) {
		this.name = name;
		this.primaryCategory = primaryCategory;
		this.id = id;
	}

	@Override
	public String primaryCategory() {
		return primaryCategory;
	}

	@Override
	public int ordinal() {
		return id;
	}

	@Override
	public String name() {
		return name;
	}

	public static Language<GlimmrTokenId> getLanguage() {
		return new GlimmrLanguageHierarchy().language();
	}

	@Override
	public String toString() {
		return "GlimmrTokenId{" + "name=" + name + ", primaryCategory=" + primaryCategory + ", id=" + id + '}';
	}


}
