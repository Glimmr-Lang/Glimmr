package roy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import roy.parser.Lexer;
import roy.parser.Parser;
import roy.typechecker.TypeChecker;
import org.graalvm.polyglot.Context;
import roy.codegen.Codegen;
import roy.rt.ContextCallback;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import static net.sourceforge.argparse4j.impl.Arguments.storeTrue;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import roy.ast.Ast;
import roy.config.JSTargetEnvironment;
import roy.errors.Errors;
import roy.fs.Fs;

/**
 *
 * @author hexaredecimal
 */
public class Compiler {

	/**
	 * @param args the command line arguments
	 */
	public void run(String[] args) {
		var parser = ArgumentParsers.newFor("glmr").build()
						.defaultHelp(true)
						.version("glmr 0.1")
						.epilog(about())
						.version("${prog} 0.0.1")
						.description("The compiler for the Glimmer programming language");

		var subparsers = parser.addSubparsers()
						.title("subcommands")
						.metavar("COMMAND")
						.dest("command");

		// glmr build
		var build = subparsers.addParser("build")
						.defaultHelp(true)
						.help("Compile the project");

		build.addArgument("--emit")
						.action(storeTrue())
						.help("Print the generated JavaScript to stdout");

		build.addArgument("--target")
						.setDefault(JSTargetEnvironment.JVM)
						.type(JSTargetEnvironment.class)
						.choices(JSTargetEnvironment.JVM, JSTargetEnvironment.BROWSER)
						.help("Specify the target environment to build for");

		build.addArgument("--outfile")
						.setDefault("./target/build.js")
						.help("Set the output file path");

		// glmr dump
		var dump = subparsers.addParser("dump")
						.defaultHelp(true)
						.help("Prints internal IR and other structures");

		dump.addArgument("tc")
						.nargs("?")
						.help("Print functions after typecheck");

		// glmr run
		var run = subparsers.addParser("run")
						.defaultHelp(true)
						.help("Execute after builing");

		run.addArgument("--repl")
						.dest("repl")
						.setDefault(false)
						.action(Arguments.storeTrue())
						.help("Launch the REPL instead of executing a file");

		run.addArgument("--")
						.nargs("*")
						.dest("extra_args")
						.help("Pass-through args");

		parser.addArgument("--version").action(Arguments.version());

		// optional: glmr help
		var help = subparsers.addParser("help")
						.help("Show help for a subcommand")
						.defaultHelp(false);

		help.addArgument("topic")
						.help("The topic or subcommand to show help for");

		try {
			Namespace res = parser.parseArgs(args);
			String command = res.getString("command");

			if ("build".equals(command)) {
				/*System.out.println("==> build:");
				System.out.println("Emit: " + res.getBoolean("emit"));
				System.out.println("Target: " + res.getString("target"));
				System.out.println("Outfile: " + res.getString("outfile"));
				 */
				var target = (JSTargetEnvironment) res.get("target");
				var code = build(target);
				if (res.getBoolean("emit")) {
					System.out.println(code);
					return;
				}
				var outfile = res.getString("outfile");
				Fs.writeToFile(new File(outfile), code);
			} else if ("dump".equals(command)) {
				System.out.println("==> dump:");
				System.out.println("TC arg: " + res.getString("tc"));
			} else if ("run".equals(command)) {
				var code = build(JSTargetEnvironment.JVM);
				String[] _args = res.getList("extra_args").toArray(String[]::new);
				boolean repl = res.getBoolean("repl");
				if (repl) {
					repl(_args);
					return;
				} else {
					runCode(code, _args);
				}
			} else if ("help".equals(command)) {
				String topic = res.getString("topic");
				parser.parseArgs(new String[]{topic, "--help"});
			}
		} catch (ArgumentParserException e) {
			parser.handleError(e);
		}
	}

	public static void runCode(String code, String[] args) {
		try (Context context = Context.newBuilder()
						.allowAllAccess(true)
						.build()) {

			context.getBindings("js").putMember("__args__", args);
			context.eval("js", code);
		}
	}

	private String build(JSTargetEnvironment target) {
		Lexer lexer = new Lexer("./examples/src/main.glmr");
		var tokens = lexer.lex();
		//tokens.forEach(System.out::println);
		///*
		var parser = new Parser(tokens);
		var nodes = parser.parse();
		// nodes.forEach(System.out::println);

		var typechecker = new TypeChecker(nodes, Parser.typeAliases);
		typechecker.process();
		var codegen = new Codegen(nodes);

		var sb = new StringBuilder();
		sb
						.append("// ")
						.append(String.format("%s", "-".repeat(15)))
						.append(" START OF MODULE CODE ")
						.append("\n")
						.append(typechecker.getModules())
						.append("\n")
						.append("// ")
						.append(String.format("%s", "-".repeat(15)))
						.append(" END OF MODULE CODE ")
						.append("\n")
						.append(typechecker.getSumTypes().trim())
						.append("\n")
						.append(codegen.gen())
						.append("\n");

		return sb.toString();
	}

	private String about() {
		return """
 ▗▄▄▖█ ▄ ▄▄▄▄  ▄▄▄▄   ▄▄▄ 
▐▌   █ ▄ █ █ █ █ █ █ █    
▐▌▝▜▌█ █ █   █ █   █ █    
▝▚▄▞▘█ █                  
  	""".indent(8);
	}

	private void repl(String[] args) {
		Errors.isrepl = true;
		var nodes = new ArrayList<Ast>();
		try (Scanner sc = new Scanner(System.in)) {
			while (true) {
				System.out.print(" > ");
				StringBuilder sb = new StringBuilder();
				var tmp = sc.nextLine();
				while (!tmp.isEmpty()) {
					sb.append(tmp);
					tmp = sc.nextLine();
				}
				nodes.addAll(exec(sb.toString()));
				var typechecker = new TypeChecker(nodes, Parser.typeAliases);
				typechecker.process();
				var codegen = new Codegen(nodes);
				var result = execute(codegen.repl(), args);
				System.out.println(" " + result);
			}
		}

	}

	private List<Ast> exec(String code) {
		Lexer lexer = new Lexer(code, null);
		var tokens = lexer.lex();
		var parser = new Parser(tokens);
		var nodes = parser.repl();
		var typechecker = new TypeChecker(nodes, Parser.typeAliases);
		typechecker.repl(nodes);
		return nodes;
	}

	public String execute(String code, String[] args) {
		try (Context context = Context.newBuilder()
						.allowAllAccess(true)
						.build()) {

			context.getBindings("js").putMember("__args__", args);
			return context.eval("js", code).toString();
		}
	}
}
