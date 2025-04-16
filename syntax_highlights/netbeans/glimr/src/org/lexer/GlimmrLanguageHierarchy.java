package org.lexer;
import java.util.*;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;

public class GlimmrLanguageHierarchy extends LanguageHierarchy<GlimmrTokenId> {

	private static List<GlimmrTokenId> tokens;
	private static Map<Integer, GlimmrTokenId> idToToken;

	/*
TOKEN :
{
  < LPAREN: "(" >
| < RPAREN: ")" >
| < LBRACE: "{" >
| < RBRACE: "}" >
| < LBRACKET: "[" >
| < RBRACKET: "]" >
| < SEMICOLON: ";" >
| < COMMA: "," >
| < DOT: "." >
}


TOKEN :
{
  < ASSIGN: "=" >
| < GT: ">" >
| < LT: "<" >
| < BANG: "!" >
| < TILDE: "~" >
| < HOOK: "?" >
| < COLON: ":" >
| < EQ: "==" >
| < LE: "<=" >
| < GE: ">=" >
| < NE: "!=" >
| < SC_OR: "||" >
| < SC_AND: "&&" >
| < INCR: "++" >
| < DECR: "--" >
| < PLUS: "+" >
| < MINUS: "-" >
| < STAR: "*" >
| < SLASH: "/" >
| < BIT_AND: "&" >
| < BIT_OR: "|" >
| < XOR: "^" >
| < REM: "%" >
| < LSHIFT: "<<" >
| < RSIGNEDSHIFT: ">>" >
| < RUNSIGNEDSHIFT: ">>>" >
| < PLUSASSIGN: "+=" >
| < MINUSASSIGN: "-=" >
| < STARASSIGN: "*=" >
| < SLASHASSIGN: "/=" >
| < ANDASSIGN: "&=" >
| < ORASSIGN: "|=" >
| < XORASSIGN: "^=" >
| < REMASSIGN: "%=" >
| < LSHIFTASSIGN: "<<=" >
| < RSIGNEDSHIFTASSIGN: ">>=" >
| < RUNSIGNEDSHIFTASSIGN: ">>>=" >
}
	*/
	private static void init() {
		tokens = Arrays.asList(new GlimmrTokenId[]{
			new GlimmrTokenId("EOF", "whitespace", 0),
			new GlimmrTokenId("WHITESPACE", "whitespace", 1),
			new GlimmrTokenId("SINGLE_LINE_COMMENT", "comment", 4),
			new GlimmrTokenId("FORMAL_COMMENT", "comment", 5),
			new GlimmrTokenId("MULTI_LINE_COMMENT", "comment", 7),
			new GlimmrTokenId("Boolean", "keyword", 9),
			new GlimmrTokenId("BREAK", "keyword", 10),
			new GlimmrTokenId("WHEN", "keyword", 11),
			new GlimmrTokenId("IS", "keyword", 12),
			new GlimmrTokenId("CONTINUE", "keyword", 13),
			new GlimmrTokenId("NUMBER", "literal", 14),
			new GlimmrTokenId("STRING", "literal", 15),
			new GlimmrTokenId("UNIT", "literal", 16),
			new GlimmrTokenId("ELSE", "keyword", 17),
			new GlimmrTokenId("FN", "keyword", 18),
			new GlimmrTokenId("WHERE", "keyword", 19),
			new GlimmrTokenId("TYPE", "keyword", 20),
			new GlimmrTokenId("FALSE", "keyword", 21),
			new GlimmrTokenId("IMPORT", "keyword", 22),
			new GlimmrTokenId("TRUE", "keyword", 23),
			new GlimmrTokenId("LOOP", "keyword", 24),
			new GlimmrTokenId("LET", "keyword", 25),
			new GlimmrTokenId("IN", "keyword", 26),
			new GlimmrTokenId("IF", "keyword", 27),
			new GlimmrTokenId("INTEGER_LITERAL", "number", 28),
			new GlimmrTokenId("DECIMAL_LITERAL", "number", 29),
			new GlimmrTokenId("HEX_LITERAL", "number", 30),
			new GlimmrTokenId("OCTAL_LITERAL", "number", 31),
			new GlimmrTokenId("FLOATING_POINT_LITERAL", "number", 32),
			new GlimmrTokenId("CHARACTER_LITERAL", "string", 33),
			new GlimmrTokenId("STRING_LITERAL", "string", 35),
			new GlimmrTokenId("IDENTIFIER", "identifier", 36),
			new GlimmrTokenId("LETTER", "identifier", 37),
			new GlimmrTokenId("PART_LETTER", "identifier", 38),
			new GlimmrTokenId("LPAREN", "operator", 39),
			new GlimmrTokenId("RPAREN", "operator", 40),
			new GlimmrTokenId("LBRACE", "operator", 41),
			new GlimmrTokenId("RBRACE", "operator", 42),
			new GlimmrTokenId("LBRACKET", "operator", 43),
			new GlimmrTokenId("RBRACKET", "operator", 44),
			new GlimmrTokenId("SEMICOLON", "operator", 45),
			new GlimmrTokenId("COMMA", "operator", 46),
			new GlimmrTokenId("DOT", "operator", 47),
			new GlimmrTokenId("AT", "operator", 84),
			new GlimmrTokenId("ASSIGN", "operator", 48),
			new GlimmrTokenId("GT", "operator", 49),
			new GlimmrTokenId("LT", "operator", 50),
			new GlimmrTokenId("BANG", "operator", 51),
			new GlimmrTokenId("TILDE", "operator", 52),
			new GlimmrTokenId("HOOK", "operator", 53),
			new GlimmrTokenId("COLON", "operator", 54),
			new GlimmrTokenId("EQ", "operator", 55),
			new GlimmrTokenId("LE", "operator", 56),
			new GlimmrTokenId("GE", "operator", 57),
			new GlimmrTokenId("NE", "operator", 58),
			new GlimmrTokenId("SC_OR", "operator", 59),
			new GlimmrTokenId("SC_AND", "operator", 60),
			new GlimmrTokenId("INCR", "operator", 61),
			new GlimmrTokenId("DECR", "operator", 62),
			new GlimmrTokenId("PLUS", "operator", 63),
			new GlimmrTokenId("MINUS", "operator", 64),
			new GlimmrTokenId("STAR", "operator", 65),
			new GlimmrTokenId("SLASH", "operator", 66),
			new GlimmrTokenId("BIT_AND", "operator", 67),
			new GlimmrTokenId("BIT_OR", "operator", 68),
			new GlimmrTokenId("XOR", "operator", 69),
			new GlimmrTokenId("REM", "operator", 70),
			new GlimmrTokenId("LSHIFT", "operator", 71),
			new GlimmrTokenId("PLUSASSIGN", "operator", 108),
			new GlimmrTokenId("MINUSASSIGN", "operator", 109),
			new GlimmrTokenId("STARASSIGN", "operator", 110),
			new GlimmrTokenId("SLASHASSIGN", "operator", 111),
			new GlimmrTokenId("ANDASSIGN", "operator", 112),
			new GlimmrTokenId("ORASSIGN", "operator", 113),
			new GlimmrTokenId("XORASSIGN", "operator", 114),
			new GlimmrTokenId("REMASSIGN", "operator", 115),
			new GlimmrTokenId("LSHIFTASSIGN", "operator", 116),
			new GlimmrTokenId("RSIGNEDSHIFTASSIGN", "operator", 117),
			new GlimmrTokenId("RUNSIGNEDSHIFTASSIGN", "operator", 118),
			new GlimmrTokenId("ELLIPSIS", "operator", 119),
			new GlimmrTokenId("RUNSIGNEDSHIFT", "operator", 120),
			new GlimmrTokenId("RSIGNEDSHIFT", "operator", 121)
		});
		
		idToToken = new HashMap<>();
		for (GlimmrTokenId token : tokens) {
			idToToken.put(token.ordinal(), token);
		}
	}

	static synchronized GlimmrTokenId getToken(int id) {
		if (idToToken == null) {
			init();
		}
		return idToToken.get(id);
	}

	@Override
	protected synchronized Collection<GlimmrTokenId> createTokenIds() {
		if (tokens == null) {
			init();
		}
		return tokens;
	}

	@Override
	protected synchronized Lexer<GlimmrTokenId> createLexer(LexerRestartInfo<GlimmrTokenId> info) {
		return new GlimmrLexer(info);
	}

	@Override
	protected String mimeType() {
		return "text/glmr";
	}

}
