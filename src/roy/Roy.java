package roy;

import roy.parser.Lexer;
import roy.parser.Parser;
import roy.typechecker.TypeChecker;

/**
 *
 * @author hexaredecimal
 */
public class Roy {

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		Lexer lexer = new Lexer("./examples/hello.roy");
		var tokens = lexer.lex();

		var parser = new Parser(tokens);
		var nodes = parser.parse();

		nodes.forEach(System.out::println);

		var typechecker = new TypeChecker(nodes);
		typechecker.process();
	}
	
}
