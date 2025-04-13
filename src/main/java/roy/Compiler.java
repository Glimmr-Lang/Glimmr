package roy;

import roy.codegen.Codegen;
import roy.parser.Lexer;
import roy.parser.Parser;
import roy.typechecker.TypeChecker;
import org.graalvm.polyglot.Context;
/**
 *
 * @author hexaredecimal
 */
public class Compiler {

	/**
	 * @param args the command line arguments
	 */
	public static void run(String[] args) {
		System.setProperty("-Dnashorn.args", "--language=es6");
		Lexer lexer = new Lexer("./examples/hello.glmr");
		var tokens = lexer.lex();
		//tokens.forEach(System.out::println);
		///*
		var parser = new Parser(tokens);
		var nodes = parser.parse();
		// nodes.forEach(System.out::println);

		var typechecker = new TypeChecker(nodes, Parser.typeAliases);
		typechecker.process();

		var codegen = new Codegen(nodes);

		var result = "// " + "-".repeat(10) + " START OF MODULE_CODE \n";
		result += typechecker.getModules();
		result += "\n// " + "-".repeat(10) + "END OF MODULE_CODE \n";
		result += "\n" + typechecker.getSumTypes().trim();
		result += "\n" + codegen.gen().trim();

		//Fs.writeToFile(new File("./out.js"), result);
		//System.out.println("" + result);
		runCode(result, args);
		//*/
	}

	
	public static void runCode(String code, String[] args) {
		try (Context context = Context.newBuilder()
						.allowAllAccess(true)
						.build()) {

			context.getBindings("js").putMember("__args__", args);
			context.eval("js", code);
		}
	}


}
