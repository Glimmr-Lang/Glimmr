package roy.codegen.jsast;

import java.util.List;
import java.util.ArrayList;
import roy.ast.Ast;
import roy.ast.AnyPattern;
import roy.ast.BindPattern;
import roy.ast.ExpressionPattern;
import roy.ast.Pattern;
import roy.ast.RString;
import roy.ast.TuplePattern;
import roy.rt.It;

/**
 *
 * @author hexaredecimal
 */
public class WhenExpression implements CodegenAst {

	private static int count = 0;
	public CodegenAst match;
	public List<WhenCases> List;
	public CodegenAst elze;

	public WhenExpression(CodegenAst match, List<WhenCases> List, CodegenAst elze) {
		this.match = match;
		this.List = List;
		this.elze = elze;
	}

	@Override
	public String toString() {
		var name = String.format("%s", allocateMatchName());
		var cond = String.format("let %s = %s;", name, match);
		StringBuilder sb = new StringBuilder();
		sb
			.append("(() => {\n")
			.append(cond.indent(4));
			
		// Track if this is the first case (for if vs else if)
		boolean isFirstCase = true;
		
		for (var _case : List) {
			var pattern = _case.cond;
			String bd;
			
			if (pattern instanceof ExpressionPattern expr_pattern) {
				bd = createBodyForExprPattern(name, expr_pattern.expr, _case.body, isFirstCase);
			} else if (pattern instanceof TuplePattern tuple_pattern) {
				bd = genTuplePattern(name, tuple_pattern, _case.body, isFirstCase);
			} else {
				continue; // Skip unsupported patterns
			}
			
			sb.append(bd.indent(4));
			isFirstCase = false;
		}

		if (elze != null) {
			sb.append(genElse(elze).indent(4));
		}

		sb.append("\n");
		sb.append("}) ();");
		return sb.toString();
	}

	private String createBodyForExprPattern(String cond, Ast pattern, CodegenAst body, boolean isFirstCase) {
		if (pattern instanceof roy.ast.Number number) {
			return genNumberPattern(cond, number, body, isFirstCase);
		}
		if (pattern instanceof RString str) {
			return genStringPattern(cond, str, body, isFirstCase);
		}

		It.todo();
		return null;
	}

	private String genNumberPattern(String cond, roy.ast.Number number, CodegenAst body, boolean isFirstCase) {
		StringBuilder sb = new StringBuilder();
		String ifStatement = isFirstCase ? "if" : "else if";
		var c = String.format("%s (%s == %s) {\n", ifStatement, cond, number.value.text);
		sb.append(c);
		c = String.format("return %s;", body.toString());
		sb.append(c.indent(4));
		sb.append("}\n");
		return sb.toString();
	}

	private String genStringPattern(String cond, RString str, CodegenAst body, boolean isFirstCase) {
		StringBuilder sb = new StringBuilder();
		String ifStatement = isFirstCase ? "if" : "else if";
		var c = String.format("%s (%s == \"%s\") {\n", ifStatement, cond, str.value.text);
		sb.append(c);
		c = String.format("return %s;", body.toString());
		sb.append(c.indent(4));
		sb.append("}\n");
		return sb.toString();
	}

	private String genElse(CodegenAst body) {
		StringBuilder sb = new StringBuilder();
		var c = String.format("return %s;", body.toString());
		sb.append(c).append("\n");
		return sb.toString();
	}

	private String allocateMatchName() {
		return String.format("_match_%d_", count++);
	}
	
	// Generate code for a tuple pattern match
	private String genTuplePattern(String matchVar, TuplePattern tuplePattern, CodegenAst body, boolean isFirstCase) {
		StringBuilder sb = new StringBuilder();
		
		// Create condition checking if the match is a Tuple - use if or else if based on position
		String ifStatement = isFirstCase ? "if" : "else if";
		sb.append(String.format("%s (%s instanceof Tuple", ifStatement, matchVar));
		
		// Check each element in the tuple
		List<String> bindings = new ArrayList<>(); // To store variable bindings
		for (int i = 0; i < tuplePattern.exprs.size(); i++) {
			Pattern elemPattern = tuplePattern.exprs.get(i);
			String fieldAccess = String.format("%s.f%d", matchVar, i);
			
			if (elemPattern instanceof ExpressionPattern exprPattern) {
				// For literal values, add direct comparison
				if (exprPattern.expr instanceof roy.ast.Number number) {
					sb.append(String.format(" && %s == %s", fieldAccess, number.value.text));
				} else if (exprPattern.expr instanceof RString str) {
					sb.append(String.format(" && %s == \"%s\"", fieldAccess, str.value.text));
				}
			} else if (elemPattern instanceof TuplePattern nestedTuplePattern) {
				// For nested tuples, check that the field is a tuple
				sb.append(String.format(" && %s instanceof Tuple", fieldAccess));
			} else if (elemPattern instanceof BindPattern bindPattern) {
				// For binding patterns, store the binding for later
				bindings.add(String.format("let %s = %s;", bindPattern.name.value.text, fieldAccess));
			} else if (elemPattern instanceof AnyPattern) {
				// Wild card pattern - no checks needed
			}
		}
		
		sb.append(") {\n");
		
		// Add nested checks for nested tuples
		int indentLevel = 4; // Initial indent level
		for (int i = 0; i < tuplePattern.exprs.size(); i++) {
			Pattern elemPattern = tuplePattern.exprs.get(i);
			if (elemPattern instanceof TuplePattern nestedTuplePattern) {
				String nestedCheck = genNestedTupleCheck(
					String.format("%s.f%d", matchVar, i),
					nestedTuplePattern
				);
				sb.append(nestedCheck.indent(indentLevel));
				indentLevel += 4; // Increase indent for each nested level
			}
		}
		
		// Add variable bindings
		for (String binding : bindings) {
			sb.append(binding.indent(indentLevel)).append("\n");
		}
		
		// Add the body
		String returnStmt = String.format("return %s;", body.toString());
		sb.append(returnStmt.indent(indentLevel)).append("\n");
		
		// Close all nested blocks
		for (int i = indentLevel - 4; i >= 4; i -= 4) {
			sb.append("}".indent(i)).append("\n");
		}
		
		sb.append("}\n");
		return sb.toString();
	}
	
	// Generate nested tuple check conditions
	private String genNestedTupleCheck(String tupleVar, TuplePattern tuplePattern) {
		StringBuilder sb = new StringBuilder();
		
		// Start with a single if condition for nested elements
		sb.append("if (");
		
		boolean firstCondition = true;
		List<String> bindings = new ArrayList<>();
		
		for (int i = 0; i < tuplePattern.exprs.size(); i++) {
			Pattern elemPattern = tuplePattern.exprs.get(i);
			String fieldAccess = String.format("%s.f%d", tupleVar, i);
			
			if (elemPattern instanceof ExpressionPattern exprPattern) {
				if (!firstCondition) {
					sb.append(" && ");
				}
				firstCondition = false;
				
				if (exprPattern.expr instanceof roy.ast.Number number) {
					sb.append(String.format("%s == %s", fieldAccess, number.value.text));
				} else if (exprPattern.expr instanceof RString str) {
					sb.append(String.format("%s == \"%s\"", fieldAccess, str.value.text));
				}
			} else if (elemPattern instanceof BindPattern bindPattern) {
				bindings.add(String.format("let %s = %s;", bindPattern.name.value.text, fieldAccess));
			}
		}
		
		sb.append(") {\n");
		
		// Add bindings for nested variables
		for (String binding : bindings) {
			sb.append(binding.indent(4)).append("\n");
		}
		
		return sb.toString();
	}
}
