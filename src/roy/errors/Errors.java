package roy.errors;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import roy.fs.Fs;
import roy.rt.It;
import roy.tokens.Token;

/**
 *
 * @author hexaredecimal
 */
public class Errors {
	public static String codeString = "";
	public static void reportSyntaxError(Token token, String message) {
		reportError(token, "Syntax Error", message);
	}

	public static void reportTypeCheckError(Token token, String message) {
		reportError(token, "Typechecking Error", message);
	}

	private static void reportError(Token token, String type, String message) {
		var span = token.span;
		String[] lines = toLines(span.filepath);
		
		var line_fmt = String.format(" %d │", span.line);
		var gap = " ".repeat(line_fmt.length());
		var gap2 = " ".repeat(line_fmt.length() - 1);
		var fmt = String.format("┌─%s:%d:%d: %s: %s", span.filepath == null ? "repl" : span.filepath, span.line, span.column, type, message);
		
		var index = span.line - 1 < lines.length ? span.line - 1 : lines.length -1;
		var code_line = lines[index].replaceAll("\t", " ".repeat(1));
		var repeat_count = token.text == null ? 1 : token.text.length();
		System.out.println(gap2 + fmt);
		System.out.println(line_fmt + " " + code_line);
		var tick = "─".repeat(span.column) +  "┘";
		var tick2 = " ".repeat(span.column) + "^"; 
		System.out.println(gap2 + "│" + tick2);
		System.out.println(gap2 + "└" + tick);
		if (span.line + 1 < lines.length) {
			line_fmt = String.format(" %d │", span.line + 1);
			System.out.println(line_fmt);
		}
		System.exit(1);
	}

	private static String[] toLines(String path) {
		List<String> lines = new ArrayList<>();

		if (path == null) {
			for (var line: codeString.lines().toList()) {
				lines.add(line);
			}
			return lines.toArray(String[]::new);
		}
		
		try (Scanner sc = new Scanner(new File(path))) {
			while (sc.hasNextLine()) {
				lines.add(sc.nextLine());
			}
		} catch (FileNotFoundException ex) {
			It.panic(ex.toString());
		}

		return lines.toArray(String[]::new);
	}
}
