package roy;

import java.io.File;
import java.util.Scanner;
import roy.codegen.Codegen;
import roy.errors.Errors;
import roy.parser.Lexer;
import roy.parser.Parser;
import roy.typechecker.TypeChecker;
import roy.types.Type;

import javax.script.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import roy.fs.Fs;
/**
 *
 * @author hexaredecimal
 */
public class Roy {

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		System.setProperty("-Dnashorn.args", "--language=es6");
		Lexer lexer = new Lexer("./examples/hello.roy");
		var tokens = lexer.lex();
		//tokens.forEach(System.out::println);
		///*
		var parser = new Parser(tokens);
		var nodes = parser.parse();
		//nodes.forEach(System.out::println);

		var typechecker = new TypeChecker(nodes, Parser.typeAliases);
		typechecker.process();

		var codegen = new Codegen(nodes);

		var result = "// " + "-".repeat(10) + " START OF MODULE_CODE \n";
		result += typechecker.getModules();
		result += "\n// " + "-".repeat(10) + "END OF MODULE_CODE \n";
		result += "\n" + typechecker.getSumTypes().trim();
		result += "\n" + codegen.gen().trim();

		Fs.writeToFile(new File("./out.js"), result);
		// System.out.println("" + result);
		//runCode(result, args);
		//*/
	}

	public static void main2(String[] args) {
		repl();
	}

	public static void repl() {
		Scanner sc = new Scanner(System.in);
		var typechecker = new TypeChecker();
		while (true) {
			System.out.print("glimmr> ");
			
			StringBuilder sb = new StringBuilder();

			while (sc.hasNextLine()) {
				var line = sc.nextLine();
				if (line.isBlank() || line.isEmpty()) break;
				sb.append(line).append("\n");
			}
			process(sb.toString(), typechecker);
		}
	}

	public static void process(String code, TypeChecker typechecker) {
		Errors.codeString = code;
		Lexer lexer = new Lexer(code, null);
		var tokens = lexer.lex();

		//tokens.forEach(System.out::println);
		///*
		var parser = new Parser(tokens);
		var nodes = parser.repl();
		nodes.forEach(System.out::println);

		typechecker.repl(nodes);
		var t = typechecker.inferProcess();

		var codegen = new Codegen(nodes);

		var result = ""; // prelude();
		result += typechecker.getSumTypes();
		result += codegen.repl();

		runCode2(result, t);
	}
	
	public static void runCode(String code, String[] args) {
		
		ScriptEngine ee = new ScriptEngineManager()
			.getEngineByName("Nashorn");
		Bindings bindings = ee.createBindings();
		bindings.put("__args__", args);	
		try {
			ee.eval(code, bindings);
		} catch (ScriptException ex) {
			Logger.getLogger(Roy.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	public static void runCode2(String code, Type type) {
		ScriptEngine ee = new ScriptEngineManager()
			.getEngineByName("Nashorn");

		try {
			var result = ee.eval(code);
			System.out.println(type + " : " + result);
		} catch (ScriptException ex) {
			Logger.getLogger(Roy.class.getName()).log(Level.SEVERE, null, ex);
		}


	}


	public static String prelude() {
		return """
	var console = {
	 log: function(msg) {
			 
	 },
	 error: function(msg) {
			 java.lang.System.err.println(msg);
	 }
	};         
  """;
	}
}
