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
	public static void reportSynaxError(Token token, String message) {
		reportError(token, "Syntax Error", message);
	}

	public static void reportTypeCheckError(Token token, String message) {
		reportError(token, "Typechecking Error", message);
	}

	private static void reportError(Token token, String type, String message) {
		var span = token.span;
		String[] lines = toLines(span.filepath);
		var fmt = String.format("%s:%d:%d: %s: %s", span.filepath, span.line, span.column, type, message);
		var line_fmt = String.format(" %d |", span.line);
		
		var index = span.line - 1 < lines.length ? span.line - 1 : lines.length -1;
		var code_line = lines[index];
		var repeat_count = token.text == null ? 1 : token.text.length();
		System.out.println(fmt);
		System.out.println(line_fmt + " " + code_line);
		var gap = " ".repeat(line_fmt.length());
		var tick = " ".repeat(span.column) + "^".repeat(repeat_count);
		System.out.println(gap + tick);
	}

	private static String[] toLines(String path) {
		List<String> lines = new ArrayList<>();

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
