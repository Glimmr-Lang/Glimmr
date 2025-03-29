package roy.typechecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import roy.ast.Arg;
import roy.ast.Ast;
import roy.ast.BinOp;
import roy.ast.BooleanValue;
import roy.ast.Identifier;
import roy.ast.Number;
import roy.ast.RClosure;
import roy.ast.RFunction;
import roy.ast.RString;
import roy.errors.Errors;
import roy.rt.It;
import roy.tokens.Span;
import roy.tokens.Token;
import roy.tokens.TokenKind;
import roy.types.AppType;
import roy.types.BooleanType;
import roy.types.FunctionType;
import roy.types.ListType;
import roy.types.NumberType;
import roy.types.StringType;
import roy.types.Type;
import roy.types.TypeVariable;
import roy.types.UnitType;

/**
 *
 * @author hexaredecimal
 */
public class TypeChecker {

	private List<Ast> nodes;
	private Map<String, Type> typeEnv;

	public TypeChecker(List<Ast> nodes) {
		this.nodes = nodes;
		this.typeEnv = new HashMap<>();
	}

	public void process() {

	}
}
