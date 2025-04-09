package roy.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import roy.errors.ErrorNode;
import roy.errors.Errors;
import roy.fs.Fs;
import roy.tokens.Span;
import roy.tokens.Token;
import roy.tokens.TokenKind;

/**
 *
 * @author hexaredecimal
 */
public class Lexer {

	private static final HashMap<String, Token> OPERATORS = new HashMap<>();

	static {
		OPERATORS.put("+", new Token(TokenKind.ADDITIVE_OPERATOR, "+"));
		OPERATORS.put("-", new Token(TokenKind.ADDITIVE_OPERATOR, "-"));

		OPERATORS.put("*", new Token(TokenKind.MULTPLICATIVE_OPERATOR, "*"));
		OPERATORS.put("/", new Token(TokenKind.MULTPLICATIVE_OPERATOR, "/"));
		OPERATORS.put("%", new Token(TokenKind.MULTPLICATIVE_OPERATOR, "%"));

		OPERATORS.put(">>", new Token(TokenKind.BITWISE_OPERATOR, ">>"));
		OPERATORS.put("<<", new Token(TokenKind.BITWISE_OPERATOR, "<<"));
		OPERATORS.put("&", new Token(TokenKind.BITWISE_OPERATOR, "&"));
		OPERATORS.put("|", new Token(TokenKind.BITWISE_OPERATOR, "|"));

		OPERATORS.put(">", new Token(TokenKind.BOOLEAN_OPERATOR, ">"));
		OPERATORS.put(">=", new Token(TokenKind.BOOLEAN_OPERATOR, ">="));
		OPERATORS.put("<", new Token(TokenKind.BOOLEAN_OPERATOR, "<"));
		OPERATORS.put("<=", new Token(TokenKind.BOOLEAN_OPERATOR, "<="));
		OPERATORS.put("==", new Token(TokenKind.BOOLEAN_OPERATOR, "=="));
		OPERATORS.put("!=", new Token(TokenKind.BOOLEAN_OPERATOR, "!="));
		OPERATORS.put("&&", new Token(TokenKind.BOOLEAN_OPERATOR, "&&"));
		OPERATORS.put("||", new Token(TokenKind.BOOLEAN_OPERATOR, "||"));
		
		OPERATORS.put("++", new Token(TokenKind.STR_CONCAT_OPERATOR, "++"));

		OPERATORS.put("=", new Token(TokenKind.ASSIGN, "="));
		OPERATORS.put(":", new Token(TokenKind.ASSIGN, ":"));

		OPERATORS.put("|>", new Token(TokenKind.PIPE, "|>"));
		OPERATORS.put("(", new Token(TokenKind.LPAREN, "("));
		OPERATORS.put(")", new Token(TokenKind.RPAREN, ")"));
		OPERATORS.put("{", new Token(TokenKind.LBRACE, "{"));
		OPERATORS.put("}", new Token(TokenKind.RBRACE, "}"));
		OPERATORS.put("[", new Token(TokenKind.LBRACKET, "["));
		OPERATORS.put("]", new Token(TokenKind.RBRACKET, "]"));

		OPERATORS.put("->", new Token(TokenKind.ARROW, "->"));
		OPERATORS.put(":", new Token(TokenKind.COLON, ":"));
		OPERATORS.put(",", new Token(TokenKind.COMMA, ","));
		OPERATORS.put(";", new Token(TokenKind.SEMI_COLON, ";"));
		OPERATORS.put(".", new Token(TokenKind.DOT, "."));
		
		OPERATORS.put("#", new Token(TokenKind.HASH, "#")); 
		OPERATORS.put("?", new Token(TokenKind.QUESTIONMARK, "?"));

	}

	private static final HashMap<String, Token> KEYWORDS = new HashMap<>();

	static {
		KEYWORDS.put("fn", new Token(TokenKind.KEYWORD, "fn"));
		KEYWORDS.put("match", new Token(TokenKind.KEYWORD, "match"));
		KEYWORDS.put("if", new Token(TokenKind.KEYWORD, "if"));
		KEYWORDS.put("else", new Token(TokenKind.KEYWORD, "else"));
		KEYWORDS.put("then", new Token(TokenKind.KEYWORD, "then"));
		KEYWORDS.put("type", new Token(TokenKind.KEYWORD, "type"));
		KEYWORDS.put("data", new Token(TokenKind.KEYWORD, "data"));
		KEYWORDS.put("let", new Token(TokenKind.KEYWORD, "let"));
		KEYWORDS.put("in", new Token(TokenKind.KEYWORD, "in"));
		KEYWORDS.put("where", new Token(TokenKind.KEYWORD, "where"));
		KEYWORDS.put("type", new Token(TokenKind.KEYWORD, "type"));
	}

	private List<ErrorNode> errors;
	private String filename;
	private List<Character> code;
	private List<Token> tokens;
	private int line, column;
	private int tmpCol;

	public Lexer(String filename) {
		this.filename = filename;
		this.code = Fs
			.readToString(filename)
			.unwrap()
			.chars()
			.boxed()
			.map((f) -> (char) f.intValue())
			.collect(Collectors.toList());
		line = 1;
		column = 1;
		tokens = new ArrayList<>();
		errors = new ArrayList<>();
	}

	public Lexer(String code, String filename) {
		this.filename = filename;
		this.code =	code.chars()
			.boxed()
			.map((f) -> (char) f.intValue())
			.collect(Collectors.toList());
		line = 1;
		column = 1;
		tokens = new ArrayList<>();
		errors = new ArrayList<>();

	}
	

	public List<Token> lex() {
		while (!code.isEmpty()) {
			tmpCol = column;
			char top = next();
			if (Character.isLetter(top) || top == '_') {
				collectIdentifier(top);
			} else if (Character.isDigit(top)) {
				collectNumber(top);
			} else if (OPERATORS.containsKey(String.valueOf(top))) {
				collectOperator(top);
			} else if (Character.isWhitespace(top)) {
				// Ignonre
			} else if (top == '"') {
				collectString();
			}
			else {
				var span = new Span(line, tmpCol, filename);
				var token = new Token(TokenKind.ID, top + "", span);
				var message = String.format("Invalid character found `%s`", top + "");
				errors.add(new ErrorNode(token, message));
			}
		}

		addToken(new Token(TokenKind.EOF, null));

		if (!errors.isEmpty()) {
			errors.forEach(err -> { Errors.reportSyntaxError(err.token, err.message);});
			System.exit(0);
		}

		return tokens;
	}

	private char peek(int offset) {
		assert code.size() < offset;
		return code.get(offset);
	}

	private char next() {
		assert !code.isEmpty();
		char c = code.removeFirst();
		column++;
		if (c == '\n') {
			line++;
			column = 1;
		}
		return c;
	}

	private void unnext(char c) {
		code.addFirst(c);
		column--;
		if (column < tmpCol) tmpCol = column;
		if (c == '\n') {
			line--;
		}
	}

	private void addToken(Token token, int start) {
		var span = new Span(line, start, filename);
		token.span = span;
		tokens.add(token);
	}

	private void addToken(Token token) {
		var span = new Span(line, column, filename);
		token.span = span;
		tokens.add(token);
	}

	private void collectIdentifier(char top) {
		StringBuilder sb = new StringBuilder();
		var col = tmpCol;
		while (Character.isLetter(top) || top == '_' || Character.isDigit(top)) {
			sb.append(top);
			if (code.isEmpty()) {
				break;
			}
			col++;
			top = next();
		}

		if (top == '\n') {
			col -= 1;
		}
		unnext(top);
		var id = sb.toString().trim();
		col = tmpCol == 0 ? col : tmpCol;
		if (KEYWORDS.containsKey(id)) {
			addToken(KEYWORDS.get(id), col);
		} else {
			var token = new Token(TokenKind.ID, id);
			addToken(token, col);
		}
	}

	private void collectNumber(char top) {
		StringBuilder sb = new StringBuilder();
		var col = tmpCol;
		while (Character.isDigit(top) || top == '_') {
			sb.append(top);
			if (code.isEmpty()) {
				break;
			}
			col++;
			top = next();
		}
		var id = sb.toString().replaceAll("_", "").trim();

		if (top == '\n') {
			col -= 1;
		}

		col = tmpCol == 0 ? col : tmpCol;
		if (top != '.') {
			unnext(top);
			var token = new Token(TokenKind.NUMBER, id);
			addToken(token, col);
			return;
		}

		top = next();
		sb.delete(0, sb.length());
		while (Character.isDigit(top) || top == '_') {
			sb.append(top);
			if (code.isEmpty()) {
				break;
			}
			col++;
			top = next();
		}

		id += '.' + sb.toString();
		if (top == '\n') {
			col -= 1;
		}

		col = tmpCol == 0 ? col : tmpCol;
		var token = new Token(TokenKind.NUMBER, id);
		addToken(token, col);
	}

	private void collectOperator(char top) {
		StringBuilder sb = new StringBuilder();

		// Single line comment support
		if (top == '/' && peek(0) == '/') {
			top = next();
			while (top != '\n' && !code.isEmpty()) 
				top = next();
			return;
		}

		// Multiline comment support
		if (top == '/' && peek(0) == '*') {
			next();
			while (top != '*' && peek(0) != '/' && !code.isEmpty()) 
				top = next();
			next();
			next();
			return;
		}


		sb.append(top);
		var peeked = peek(0);
		if (OPERATORS.containsKey(sb.toString() + peeked)) {
			top = next();
			sb.append(top);
		}

		var op = sb.toString();
		addToken(OPERATORS.get(op), tmpCol);
	}

	private void collectString() {
		StringBuilder sb = new StringBuilder();
		var t = next();
		while (t != '"') {
			sb.append(t);
			if (t == '\\') sb.append("\\");
			if (t == '\n')  {
				var span = new Span(line -1, tmpCol + sb.toString().length(), filename);
				var token = new Token(TokenKind.ID, t + "", span);
				var message = String.format("Expected `\"` at the end of the string literal");
				errors.add(new ErrorNode(token, message));
				break;
			}
			t = next();
		}

		var token = new Token(TokenKind.STRING, sb.toString());
		addToken(token);
	}

}
