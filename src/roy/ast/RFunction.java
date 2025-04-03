package roy.ast;

import java.util.ArrayList;
import java.util.List;
import roy.tokens.Token;
import roy.types.Type;

/**
 *
 * @author hexaredecimal
 */
public class RFunction implements Ast {

	public Token name;
	public List<Arg> args;
	public Type type;
	public Ast body;
	public List<Ast> where;

	public RFunction(Token name, List<Arg> args, Type type, Ast body) {
		this.name = name;
		this.args = args;
		this.type = type;
		this.body = body;
		this.where = new ArrayList<>();
	}

	public RFunction(Token name, List<Arg> args, Type type, Ast body, List<Ast> where) {
		this.name = name;
		this.args = args;
		this.type = type;
		this.body = body;
		this.where = where;
	}

	@Override
	public String toString() {
		var a = args.toString();
		a = a.substring(0, a.length() - 1);
		a = a.substring(1);

		StringBuilder sb = new StringBuilder();

		if (!where.isEmpty()) {
			sb.append("\n");
			sb.append("where".indent(4));
			for (var f : where) {
				sb.append(f.toString().indent(8));
			}
			sb.append(";".indent(4));
		}
		var _where = sb.toString();
		return String.format("fn %s (%s) : %s => %s %s", name.text, a, type, body, _where);
	}
}
