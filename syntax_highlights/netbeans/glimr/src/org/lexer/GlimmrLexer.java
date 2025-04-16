package org.lexer;

/**
 *
 * @author hexaredecimal
 */

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.javacc.GlimmrParserTokenManager;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.javacc.JavaCharStream;
import org.javacc.Token;

class GlimmrLexer implements Lexer<GlimmrTokenId> {

	private LexerRestartInfo<GlimmrTokenId> info;
	private GlimmrParserTokenManager javaParserTokenManager;

	GlimmrLexer(LexerRestartInfo<GlimmrTokenId> info) {
		this.info = info;
		CharSequence chars = info.input().readText();
		JavaCharStream stream = new JavaCharStream(info.input());
		javaParserTokenManager = new GlimmrParserTokenManager(stream);
	}

	@Override
	public org.netbeans.api.lexer.Token<GlimmrTokenId> nextToken() {
		Token token = javaParserTokenManager.getNextToken();
		if (info.input().readLength() < 1) {
			return null;
		}
		return info.tokenFactory().createToken(GlimmrLanguageHierarchy.getToken(token.kind));
	}

	@Override
	public Object state() {
		return null;
	}

	@Override
	public void release() {
	}

}
