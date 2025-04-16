package org.glmr;


import org.lexer.GlimmrTokenId;
import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;


@LanguageRegistration(mimeType = "text/glmr")
public class GlimmrLanguage extends DefaultLanguageConfig {

	@Override
	public Language getLexerLanguage() {
		return GlimmrTokenId.getLanguage();
	}

	@Override
	public String getDisplayName() {
		return "Glimmr";
	}
}
