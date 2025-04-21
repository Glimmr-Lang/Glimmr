package roy.typechecker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;
import roy.ast.*;
import roy.codegen.Codegen;
import roy.codegen.jsast.JFunctionObject;
import roy.errors.Errors;
import roy.rt.It;
import roy.tokens.Span;
import roy.tokens.Token;
import roy.tokens.TokenKind;
import roy.types.*;

/**
 *
 * @author hexaredecimal
 */
public class TypeChecker {

	private List<Ast> nodes;
	private Map<String, Type> typeEnv;
	private Map<String, Type> localSymbolTable; // Symbol table for storing local variables that are declared using let/ 
	private Map<TypeVariable, Type> substitutions;
	private Map<String, RFunction> functionTable; // Function table for all functions
	private Map<String, Boolean> checkedFunctions; // Track which functions have been checked
	private Map<String, Type> functionTypes; // Store function types separately
	private Map<String, TypeAlias> typeAliases; // Store type aliases or named types
	private Map<String, JFunctionObject> sumTypes; // Holds sumtypes that are going to be codegened
	private Map<String, RModule> modules; // Holds sumtypes that are going to be codegened

	public TypeChecker(List<Ast> nodes) {
		this.nodes = nodes;
		this.typeEnv = new HashMap<>();
		this.substitutions = new HashMap<>();
		this.functionTable = new HashMap<>();
		this.checkedFunctions = new HashMap<>();
		this.functionTypes = new HashMap<>();
		this.localSymbolTable = new HashMap<>();
		this.typeAliases = new HashMap<>();
		this.sumTypes = new HashMap<>();
		this.modules = new HashMap<>();

		processAliases(nodes);
		//System.out.println("" + typeAliases.toString());
		// First pass: register all functions in the function table
		registerFunctionsAndTypes();
	}

	public TypeChecker(List<Ast> nodes, List<Ast> aliases) {
		this.nodes = nodes;
		this.typeEnv = new HashMap<>();
		this.substitutions = new HashMap<>();
		this.functionTable = new HashMap<>();
		this.checkedFunctions = new HashMap<>();
		this.functionTypes = new HashMap<>();
		this.localSymbolTable = new HashMap<>();
		this.typeAliases = new HashMap<>();
		this.sumTypes = new HashMap<>();
		this.modules = new HashMap<>();

		processAliases(aliases);
		processAliases(nodes);
		//System.out.println("" + typeAliases.toString());
		// First pass: register all functions in the function table
		registerFunctionsAndTypes();
	}

	public TypeChecker() {
		this.typeEnv = new HashMap<>();
		this.substitutions = new HashMap<>();
		this.functionTable = new HashMap<>();
		this.checkedFunctions = new HashMap<>();
		this.functionTypes = new HashMap<>();
		this.localSymbolTable = new HashMap<>();
		this.typeAliases = new HashMap<>();
		this.sumTypes = new HashMap<>();
		this.nodes = new ArrayList<>();
		processAliases(nodes);
		registerFunctionsAndTypes();
	}

	public void repl(List<Ast> nodes) {
		this.nodes = nodes;
	}

	public String getSumTypes() {
		List<JFunctionObject> list = new ArrayList<>();
		for (var value : sumTypes.values()) {
			list.add(value);
		}

		return Arrays.stream(list.toArray())
						.map(Object::toString)
						.collect(Collectors.joining("\n"));
	}

	public List<Ast> getAliases() {
		List<Ast> list = new ArrayList<>();
		for (var value : typeAliases.values()) {
			list.add(value);
		}

		return list;
	}

	public String getModules() {
		List<Ast> list = new ArrayList<>();
		for (var value : modules.values()) {
			list.addAll(value.decls);
		}
		var codegen = new Codegen(list);
		return codegen.gen();
	}

	private void processAliases(List<Ast> _alias) {
		for (Ast node : _alias) {
			if (node instanceof TypeAlias alias) {
				typeAliases.put(alias.name.text, alias);
			}
		}
	}

	// First pass: collect all function declarations
	private void registerFunctionsAndTypes() {
		for (Ast node : nodes) {
			if (node instanceof AnnotatedFunction func && func.isExtern) {
				addExternFunction(func);
			} else if (node instanceof AnnotatedFunction func && func.isExport) {
				addFunction(func.func);
			} else if (node instanceof RFunction func) {
				addFunction(func);
			} else if (node instanceof RModule mod) {
				mod.decls.forEach(_node -> {
					if (_node instanceof TypeAlias) {
						return;
					}

					if (_node instanceof AnnotatedFunction func && func.isExtern) {
						return;
					}

					if (_node instanceof AnnotatedFunction func && func.isExport) {
						checkArgs(func.func);
						inferFunction(func.func);
						checkedFunctions.remove(func.func.name.text);
						functionTable.remove(func.func.name.text);
						return;
					}
					if (_node instanceof RFunction func) {
						checkArgs(func);
						inferFunction(func);
						checkedFunctions.remove(func.name.text);
						functionTable.remove(func.name.text);
						return;
					}

				});
				modules.put(mod.name.text, mod);
			}
		}
	}

	private void checkArgs(RFunction func) {
		for (var arg : func.args) {
			if (arg.type instanceof NamedType nm) {
				if (!typeAliases.containsKey(nm.name.text)) {
					System.out.println("" + typeAliases.keySet());
					Errors.reportTypeCheckError(arg.name, "Type `" + nm.name.text + "` is unknown in this context.");
				}
			}
		}
	}

	private void addExternFunction(AnnotatedFunction func) {
		String name = func.func.name.text;
		functionTable.put(name, func.func);
		checkedFunctions.put(name, true);

		// Create a proper function type for extern functions
		List<Type> argTypes = new ArrayList<>();
		for (Arg arg : func.func.args) {
			if (arg.type != null) {
				argTypes.add(arg.type);
			} else {
				// Extern functions must have explicit types
				argTypes.add(freshTypeVar(arg.name));
			}
		}

		// Use the return type specified in the function
		Type returnType = func.func.type != null ? func.func.type : freshTypeVar(func.func.name);

		// Create and store the function type
		functionTypes.put(name, new FunctionType(argTypes, returnType));
	}

	private void addFunction(RFunction func) {
		String name = func.name.text;
		functionTable.put(name, func);
		checkedFunctions.put(name, false);

		// Create a skeleton function type with fresh type variables
		List<Type> argTypes = new ArrayList<>();
		for (Arg arg : func.args) {
			if (arg.type != null) {
				argTypes.add(arg.type);
			} else {
				argTypes.add(freshTypeVar(arg.name));
			}
		}

		Type returnType = func.type != null ? func.type : freshTypeVar(func.name);
		functionTypes.put(name, new FunctionType(argTypes, returnType));

		// Resolve any type aliases in function signature
		if (func.type != null) {
			func.type = resolveTypeAlias(func.type);
		}

		for (Arg arg : func.args) {
			if (arg.type != null) {
				arg.type = resolveTypeAlias(arg.type);
			}
		}
	}

	public void process() {
		// Process all function declarations to set their types
		var arr = functionTable.entrySet().stream().toList();
		for (int i = 0; i < arr.size(); i++) {
			var entry = arr.get(i);
			// Create completely new maps for each function
			substitutions = new HashMap<>();
			typeEnv = new HashMap<>();
			localSymbolTable = new HashMap<>();
			// Double-check that they're completely empty
			substitutions.clear();
			typeEnv.clear();
			localSymbolTable.clear();

			String functionName = entry.getKey();
			if (!checkedFunctions.get(functionName)) {
				TypeVariable.reset();
				inferFunction(entry.getValue());
			}
		}

		/*
		for (var kv: functionTypes.entrySet()) {
			var name = kv.getKey();
			var type = kv.getValue(); 
			System.out.println("fn " + name + " : " + type);
		} */
		// Print results
	}

	public Type inferProcess() {
		if (nodes.getFirst() instanceof TypeAlias ta) {
			return ta.type;
		}

		return infer(nodes.getFirst()).type;
	}

	// Create a fresh type variable using a token
	private TypeVariable freshTypeVar(Token token) {
		return new TypeVariable(token);
	}

	private TypeVariable freshTypeVar(Span span) {
		return new TypeVariable(span);
	}

	// Recursive type inference for any AST node
	public CheckedNode infer(Ast node) {
		if (node instanceof roy.ast.Number) {
			return new CheckedNode(new NumberType(), node);
		} else if (node instanceof RString) {
			return new CheckedNode(new StringType(), node);
		} else if (node instanceof BooleanValue) {
			return new CheckedNode(new BooleanType(), node);
		} else if (node instanceof Identifier) {
			return inferIdentifier((Identifier) node);
		} else if (node instanceof BinOp) {
			return inferBinOp((BinOp) node);
		} else if (node instanceof RFunction) {
			return inferFunction((RFunction) node);
		} else if (node instanceof RClosure) {
			return inferClosure((RClosure) node);
		} else if (node instanceof Call) {
			return inferCall((Call) node);
		} else if (node instanceof IfElse) {
			return inferIfElse((IfElse) node);
		} else if (node instanceof RObject) {
			return inferRObject((RObject) node);
		} else if (node instanceof Tuple) {
			return inferTuple((Tuple) node);
		} else if (node instanceof Block) {
			return inferBlock((Block) node);
		} else if (node instanceof LetIn) {
			return inferLetIn((LetIn) node);
		} else if (node instanceof Unit) {
			return inferUnit((Unit) node);
		} else if (node instanceof FieldAccess) {
			return inferFieldAccess((FieldAccess) node);
		} else if (node instanceof GroupExpression expr) {
			return inferGroupExpression(expr);
		} else if (node instanceof ModuleAccess ma) {
			return inferModuleAccess(ma);
		} else if (node instanceof When when) {
			return inferWhenExpression(when);
		} else if (node instanceof Array array) {
			return inferArray(array);
		} else {
			// For other node types, report an error
			Token token = getTokenFromAst(node);
			Errors.reportTypeCheckError(token, "Type inference not implemented for: " + node.getClass().getName());
			System.exit(0);
			return null;
		}
	}

	private CheckedNode inferArray(Array array) {
		// If the array is empty, we can't infer its type, so use a fresh type variable
		if (array.elements.isEmpty()) {
			return new CheckedNode(new ListType(freshTypeVar(array.start)), array);
		}

		// Infer types for all elements in the array
		List<Type> elementTypes = new ArrayList<>();
		for (Ast element : array.elements) {
			CheckedNode checkedElement = infer(element);
			elementTypes.add(checkedElement.type);
		}

		// Check if all elements have the same type
		Type firstType = elementTypes.get(0);
		boolean allSameType = true;

		for (int i = 1; i < elementTypes.size(); i++) {
			try {
				unify(firstType, elementTypes.get(i), array.elements.get(i));
			} catch (TypeMismatchError e) {
				allSameType = false;
				break;
			}
		}

		// If all elements have the same type, use that type
		if (allSameType) {
			return new CheckedNode(new ListType(firstType), array);
		}

		// Otherwise, create a union type from all unique element types
		Set<Type> uniqueTypes = new HashSet<>(elementTypes);
		List<Type> unionTypes = new ArrayList<>(uniqueTypes);

		// Create a union type and return an array of that union type
		UnionType unionType = new UnionType(unionTypes);
		return new CheckedNode(new ListType(unionType), array);
	}

	private CheckedNode inferModuleAccess(ModuleAccess ma) {
		var lhs = ma.module.toString();
		if (!modules.containsKey(lhs)) {
			Token token = getTokenFromAst(ma.field);
			Errors.reportTypeCheckError(token, "Module `" + lhs + "` is not found. Try adding an import statement");
		}

		var mod = modules.get(lhs);
		var func = extractNode(lhs, mod.decls, ma.field.value);
		if (func instanceof AnnotatedFunction fx && fx.isExport) {
			return inferFunction(fx.func);
		}
		if (func instanceof AnnotatedFunction fx && fx.isExtern) {
			return new CheckedNode(makeExternFunctionType(fx), func);
		}
		return inferFunction((RFunction) func);
	}

	private Type makeExternFunctionType(AnnotatedFunction func) {
		String name = func.func.name.text;
		functionTable.put(name, func.func);
		checkedFunctions.put(name, true);

		// Create a proper function type for extern functions
		List<Type> argTypes = new ArrayList<>();
		for (Arg arg : func.func.args) {
			if (arg.type != null) {
				argTypes.add(arg.type);
			} else {
				// Extern functions must have explicit types
				argTypes.add(freshTypeVar(arg.name));
			}
		}

		// Handle the special case for no arguments - never allow an extern function to have zero arguments
		if (argTypes.isEmpty() && func.func.body instanceof JSCode) {
			// If there are no arguments but it's clearly meant to take arguments, add a generic type
			argTypes.add(new TypeVariable(new Token(TokenKind.ID, "a", func.func.name.span)));
		}

		// Use the return type specified in the function
		Type returnType = func.func.type != null ? func.func.type : freshTypeVar(func.func.name);

		// Create and store the function type
		return new FunctionType(argTypes, returnType);
	}

	private Ast extractNode(String module, List<Ast> nodes, Token name) {
		for (var ast : nodes) {
			if (ast instanceof RFunction func && func.name.text.equals(name.text)) {
				return func;
			}
			if (ast instanceof AnnotatedFunction afunc && afunc.func.name.text.equals(name.text)) {
				return afunc;
			}
		}

		Errors.reportSyntaxError(name, "No function `" + name.text + "` found in module " + module);
		It.unreachable();
		return null;
	}

	private boolean hasExplicitType(Identifier id) {
		String name = id.value.text;

		// Check if it's in the local symbol table with a non-TypeVariable type
		if (localSymbolTable.containsKey(name)) {
			Type type = localSymbolTable.get(name);
			if (!(type instanceof TypeVariable)) {
				return true;
			}
		}

		// Check if it's in the type environment with a non-TypeVariable type
		if (typeEnv.containsKey(name)) {
			Type type = typeEnv.get(name);
			if (!(type instanceof TypeVariable)) {
				return true;
			}
		}

		// For function parameters, we need to check if they have type annotations
		// Look through function arguments to see if this identifier has an explicit type
		for (var funcEntry : functionTable.entrySet()) {
			RFunction func = funcEntry.getValue();
			for (Arg arg : func.args) {
				if (arg.name.text.equals(name) && arg.type != null && !(arg.type instanceof TypeVariable)) {
					return true;
				}
			}
		}

		return false;
	}

	private CheckedNode inferWhenExpression(When when) {
		// First, infer the type of the match expression
		CheckedNode matchChecked = infer(when.match);
		Type matchType = matchChecked.type;

		// Check if the match expression has an explicit type annotation
		boolean hasExplicitType = false;

		// If matching against a function parameter, check if it has an explicit type
		if (when.match instanceof Identifier id) {
			hasExplicitType = hasExplicitType(id);
		}

		// Process each case pattern and body
		List<Type> patternTypes = new ArrayList<>();
		List<Type> bodyTypes = new ArrayList<>();
		List<BindPattern> bindPatterns = new ArrayList<>();
		Map<String, Type> savedLocalSymbols = new HashMap<>(localSymbolTable);

		// First pass - collect all pattern types and identify bind patterns
		for (MatchCase casse : when.cases) {
			// Infer pattern type and add to pattern types list
			Type patternType = inferPatternType(casse.pattern, matchType);
			patternTypes.add(patternType);

			// If match expression has explicit type, verify pattern compatibility
			if (hasExplicitType) {
				try {
					unify(matchType, patternType, casse.pattern);
				} catch (TypeMismatchError e) {
					// For explicit types, report an error when patterns don't match
					Token token = getTokenFromAst(casse.pattern);
					Errors.reportTypeCheckError(token,
									"Pattern of type " + patternType + " is not compatible with match expression of type " + matchType);
					System.exit(0);
				}
			}

			// Keep track of bind patterns for later unification
			if (casse.pattern instanceof BindPattern) {
				bindPatterns.add((BindPattern) casse.pattern);
			}
		}

		// Identify concrete types among the patterns (non-TypeVariable types)
		List<Type> concreteTypes = new ArrayList<>();
		for (Type type : patternTypes) {
			if (!(type instanceof TypeVariable)) {
				concreteTypes.add(type);
			}
		}

		// Try to unify bind pattern variables with concrete types if possible
		if (!concreteTypes.isEmpty() && !bindPatterns.isEmpty()) {
			Type concreteUnion = concreteTypes.size() == 1
							? concreteTypes.get(0) : new UnionType(concreteTypes);

			for (BindPattern bindPattern : bindPatterns) {
				String varName = bindPattern.name.value.text;
				if (localSymbolTable.containsKey(varName)) {
					Type varType = localSymbolTable.get(varName);

					// Try to unify the variable with the concrete type(s)
					try {
						if (varType instanceof TypeVariable) {
							substituteTypeVar((TypeVariable) varType, concreteUnion);
							localSymbolTable.put(varName, concreteUnion);
						}
					} catch (Exception e) {
						// If unification fails, keep the original type variable
					}
				}
			}
		}

		// Second pass - infer body types after binding pattern unification
		// and ensure pattern bindings are consistent with match type
		for (MatchCase casse : when.cases) {
			// Make sure pattern types are unified with match type
			Type patternType = inferPatternType(casse.pattern, matchType);

			// Ensure bound variables in patterns are properly unified with the match type
			if (casse.pattern instanceof BindPattern bindPattern) {
				String varName = bindPattern.name.value.text;
				if (localSymbolTable.containsKey(varName)) {
					// The variable should have the match type (which might be a union by now)
					try {
						Type varType = localSymbolTable.get(varName);
						if (!matchType.equals(varType)) {
							// Use the match type for this variable
							if (varType instanceof TypeVariable) {
								substituteTypeVar((TypeVariable) varType, matchType);
							}
							localSymbolTable.put(varName, matchType);
						}
					} catch (Exception e) {
						// If unification fails, keep going
					}
				}
			}

			// Now infer the body type with the updated symbol table
			CheckedNode bodyChecked = infer(casse.body);

			// Apply substitutions to get most concrete type
			Type bodyType = applySubstitutions(bodyChecked.type);
			bodyTypes.add(bodyType);
		}

		// Restore original local symbol table
		localSymbolTable.clear();
		localSymbolTable.putAll(savedLocalSymbols);

		// Handle else case if present
		if (when._else != null) {
			CheckedNode elseChecked = infer(when._else);
			bodyTypes.add(elseChecked.type);
		}

		// For explicit types, use the match type directly
		if (hasExplicitType) {
			matchType = applySubstitutions(matchType);
		} else {
			// Create union type of all pattern types if they're different
			Type matchUnionType;
			if (patternTypes.size() > 1) {
				boolean allSamePatternType = true;
				Type firstPatternType = patternTypes.get(0);
				for (int i = 1; i < patternTypes.size(); i++) {
					if (!patternTypes.get(i).equals(firstPatternType)) {
						allSamePatternType = false;
						break;
					}
				}

				if (!allSamePatternType) {
					// Different pattern types, create a union
					matchUnionType = new UnionType(patternTypes);
				} else {
					// All pattern types are the same
					matchUnionType = firstPatternType;
				}
			} else if (patternTypes.size() == 1) {
				matchUnionType = patternTypes.get(0);
			} else {
				// No patterns (shouldn't happen)
				matchUnionType = matchType;
			}

			// Try to unify the match expression with the union of pattern types
			try {
				unify(matchType, matchUnionType, when.match);
				// Apply substitutions to propagate the constraint
				applyAllSubstitutions();
			} catch (TypeMismatchError e) {
				// We're being permissive, so we don't want to error out here
				// Instead, use the union type as the match type
				matchType = matchUnionType;
			}
		}

		// Apply substitutions to get the most specific match type
		matchType = applySubstitutions(matchType);

		// Now look at the body types and replace any type variables that should be the match type
		// This handles cases like "is b -> b" where b should be the match type
		for (int i = 0; i < bodyTypes.size(); i++) {
			Type bodyType = bodyTypes.get(i);
			if (bodyType instanceof TypeVariable) {
				// Check if this type variable comes from a binding pattern used in the body
				for (BindPattern bindPattern : bindPatterns) {
					String varName = bindPattern.name.value.text;
					if (bodyType.toString().contains(varName)) {
						// This is likely the same variable - replace with match type
						bodyTypes.set(i, matchType);
						break;
					}
				}
			}
		}

		// Determine the output type (union of all body types or single type if all the same)
		Type outputType;
		if (bodyTypes.size() > 1) {
			// Check if all body types are the same
			boolean allSameBodyType = true;
			Type firstBodyType = bodyTypes.get(0);
			for (int i = 1; i < bodyTypes.size(); i++) {
				if (!bodyTypes.get(i).equals(firstBodyType)) {
					allSameBodyType = false;
					break;
				}
			}

			if (allSameBodyType) {
				outputType = firstBodyType;
			} else {
				outputType = new UnionType(bodyTypes);
			}
		} else if (bodyTypes.size() == 1) {
			outputType = bodyTypes.get(0);
		} else {
			// No body types (shouldn't happen)
			outputType = new TypeVariable(getTokenFromAst(when));
		}

		return new CheckedNode(outputType, when);
	}

	/**
	 * Infer the type of a pattern
	 */
	private Type inferPatternType(Pattern pattern, Type matchType) {
		if (pattern instanceof AnyPattern) {
			// Wildcard pattern matches anything
			return matchType;
		} else if (pattern instanceof BindPattern bindPattern) {
			// Check if we're matching against an explicitly typed value
			boolean isExplicitMatchType = !(matchType instanceof TypeVariable);

			// Bind pattern creates a variable of the match type
			String name = bindPattern.name.value.text;

			// Check if we already have a binding for this name
			if (localSymbolTable.containsKey(name)) {
				Type existingType = localSymbolTable.get(name);

				// If matching against a concrete type, use that type
				if (isExplicitMatchType) {
					// For explicit types, try to unify with existing type
					if (existingType instanceof TypeVariable) {
						try {
							substituteTypeVar((TypeVariable) existingType, matchType);
							localSymbolTable.put(name, matchType);
							return matchType;
						} catch (Exception e) {
							// If unification fails, error if matching against explicit type
							Errors.reportTypeCheckError(bindPattern.name.value,
											"Cannot match binding pattern of type " + existingType + " with expression of type " + matchType);
							System.exit(0);
						}
					}
					// Try to unify existing type with match type
					try {
						unify(existingType, matchType, bindPattern);
						return existingType;
					} catch (TypeMismatchError e) {
						Errors.reportTypeCheckError(bindPattern.name.value,
										"Cannot match binding pattern of type " + existingType + " with expression of type " + matchType);
						System.exit(0);
					}
				}

				// If the existing type is a type variable and match type is concrete, use match type
				if (existingType instanceof TypeVariable && !isExplicitMatchType) {
					try {
						substituteTypeVar((TypeVariable) existingType, matchType);
						localSymbolTable.put(name, matchType);
						return matchType;
					} catch (Exception e) {
						// If unification fails, keep existing type
						return existingType;
					}
				}
				return existingType;
			}

			// Otherwise, create a new binding
			// Use the match type directly if it's concrete, otherwise create a fresh type var
			Type bindType = matchType instanceof TypeVariable
							? freshTypeVar(bindPattern.name.value) : matchType;

			// Add the binding to local symbol table
			localSymbolTable.put(name, bindType);
			return bindType;
		} else if (pattern instanceof ExpressionPattern exprPattern) {
			// For literal patterns, infer the type of the expression
			CheckedNode exprChecked = infer(exprPattern.expr);
			Type patternType = exprChecked.type;

			// If we're matching against a concrete type, enforce compatibility
			if (!(matchType instanceof TypeVariable)) {
				try {
					unify(matchType, patternType, exprPattern.expr);
				} catch (TypeMismatchError e) {
					// For explicit match types, this is an error
					Errors.reportTypeCheckError(getTokenFromAst(exprPattern.expr),
									"Pattern of type " + patternType + " is not compatible with match expression of type " + matchType);
					System.exit(0);
				}
			}

			// Return the concrete type of the expression pattern
			return patternType;
		} else if (pattern instanceof ConsPattern consPattern) {
			// For constructor patterns, verify constructor type
			CheckedNode consChecked = infer(consPattern.name);
			return consChecked.type;
		} else if (pattern instanceof ListPattern listPattern) {
			// For list patterns, ensure each element matches
			if (!(matchType instanceof ListType)) {
				// If matching against explicit non-list type, error
				if (!(matchType instanceof TypeVariable)) {
					Errors.reportTypeCheckError(getTokenFromAst(listPattern),
									"Cannot match list pattern against non-list type " + matchType);
					System.exit(0);
				}
				// If inferred, this pattern won't match
				return matchType;
			}
			return matchType;
		} else if (pattern instanceof TuplePattern tuplePattern) {
			// For tuple patterns, check each element
			if (!(matchType instanceof TupleType)) {
				// If matching against explicit non-tuple type, error
				if (!(matchType instanceof TypeVariable)) {
					Errors.reportTypeCheckError(getTokenFromAst(tuplePattern),
									"Cannot match tuple pattern against non-tuple type " + matchType);
					System.exit(0);
				}
				// If inferred, this pattern won't match
				return matchType;
			}
			return matchType;
		} else if (pattern instanceof ObjectPattern objPattern) {
			// For object patterns, check each field
			if (!(matchType instanceof ObjectType)) {
				// If matching against explicit non-object type, error
				if (!(matchType instanceof TypeVariable)) {
					Errors.reportTypeCheckError(getTokenFromAst(objPattern),
									"Cannot match object pattern against non-object type " + matchType);
					System.exit(0);
				}
				// If inferred, this pattern won't match
				return matchType;
			}
			return matchType;
		}

		// Default case
		return matchType;
	}

	private CheckedNode inferGroupExpression(GroupExpression expr) {
		var result = infer(expr.expr);
		return new CheckedNode(result.type, expr);
	}

	private CheckedNode inferFieldAccess(FieldAccess fieldAccess) {
		var expr = infer(fieldAccess.obj);
		var expr_type = expr.type;

		if (!(expr_type instanceof ObjectType) && !(expr_type instanceof TypeVariable)) {
			Token token = getTokenFromAst(fieldAccess.obj);
			Errors.reportTypeCheckError(token, "Field access is not valid for an expression of type " + expr_type + ". Field access only works for object types, but found type: " + expr_type);
			System.exit(0);
			return null;
		}

		// If it's a type variable, constrain it to be an object with the accessed field
		if (expr_type instanceof TypeVariable) {
			// Create a fresh type variable for the field type
			TypeVariable fieldType = freshTypeVar(fieldAccess.field.value);

			// Create an object type with just the accessed field
			HashMap<Token, Type> fields = new HashMap<>();
			fields.put(fieldAccess.field.value, fieldType);
			var constraintType = new ObjectType(fields);

			try {
				// Unify the expression with our constraint object type
				unify(expr_type, constraintType, fieldAccess);
				applyAllSubstitutions();

				// Get the updated field type after unification
				Type resultType = applySubstitutions(fieldType);
				return new CheckedNode(resultType, fieldAccess);
			} catch (TypeMismatchError e) {
				Errors.reportTypeCheckError(
								fieldAccess.field.value,
								"Cannot access field '" + fieldAccess.field.value.text
								+ "' on expression of type " + expr_type
				);
				System.exit(0);
				return null;
			}
		}

		// For concrete object types, find the matching field
		var obj = (ObjectType) expr_type;
		for (var kv : obj.fields.entrySet()) {
			var field = kv.getKey();
			var type = kv.getValue();

			if (field.text.equals(fieldAccess.field.value.text)) {
				return new CheckedNode(type, fieldAccess);
			}
		}

		// Field not found in the object
		var field = fieldAccess.field.value;
		Errors.reportTypeCheckError(field, "Field `" + field.text + "` is not part of object type " + obj);
		System.exit(0);
		return null;
	}

	private CheckedNode inferUnit(Unit unit) {
		if (!sumTypes.containsKey("Unit")) {
			var t = new JFunctionObject("Unit", 0);
			sumTypes.put("Unit", t);
		}
		return new CheckedNode(new UnitType(), unit);
	}

	private CheckedNode inferLetIn(LetIn letin) {
		for (var let : letin.lets) {
			var name = let.name.text;
			var expr = infer(let.expr);
			try {
				unify(let.type, expr.type, expr.node);
				localSymbolTable.put(name, expr.type);
			} catch (TypeMismatchError e) {
				Errors.reportTypeCheckError(let.name, "Cannot assign type " + expr.type + " to variable `" + name + "` of type " + let.type);
				System.exit(0);
				return null;
			}
		}

		return new CheckedNode(infer(letin.in).type, letin);
	}

	private CheckedNode inferBlock(Block block) {
		var exprs = block.exprs;
		for (int i = 0; i < exprs.size() - 1; i++) {
			var top = exprs.get(i);
			infer(top);
		}
		if (exprs.isEmpty()) {
			return new CheckedNode(new UnitType(), block);
		}
		return infer(exprs.getLast());
	}

	private Token getTokenFromAst(Ast ast) {
		if (ast instanceof roy.ast.Number) {
			return ((roy.ast.Number) ast).value;
		} else if (ast instanceof RString) {
			return ((RString) ast).value;
		} else if (ast instanceof BooleanValue) {
			return ((BooleanValue) ast).value;
		} else if (ast instanceof Identifier) {
			return ((Identifier) ast).value;
		} else if (ast instanceof BinOp) {
			return ((BinOp) ast).op;
		} else if (ast instanceof RFunction) {
			return ((RFunction) ast).name;
		} else if (ast instanceof Call) {
			return getTokenFromAst(((Call) ast).expr);
		} else if (ast instanceof RClosure node) {
			return node.name;
		} else if (ast instanceof IfElse ief) {
			return getTokenFromAst(ief.cond);
		} else if (ast instanceof RObject obj) {
			var first = (Ast) obj.obj.values().toArray()[0];
			return getTokenFromAst(first);
		} else if (ast instanceof Tuple tupl) {
			return getTokenFromAst(tupl.values.getFirst());
		} else if (ast instanceof Block block) {
			return getTokenFromAst(block.exprs.getFirst());
		} else if (ast instanceof LetIn let) {
			return getTokenFromAst(let.lets.getFirst());
		} else if (ast instanceof Unit unit) {
			return new Token(TokenKind.LPAREN, "()");
		} else if (ast instanceof FieldAccess fa) {
			return getTokenFromAst(fa.field);
		} else if (ast instanceof GroupExpression expr) {
			return getTokenFromAst(expr.expr);
		} else if (ast instanceof ModuleAccess ma) {
			return getTokenFromAst(ma.module);
		} else if (ast instanceof When when) {
			return getTokenFromAst(when.match);
		} else if (ast instanceof ExpressionPattern pattern) {
			return getTokenFromAst(pattern.expr);
		} else if (ast instanceof BindPattern pattern) {
			return pattern.name.value;
		} else if (ast instanceof AnyPattern) {
			return new Token(TokenKind.ID, "_");
		} else if (ast instanceof ConsPattern pattern) {
			return getTokenFromAst(pattern.name);
		} else if (ast instanceof ListPattern pattern && !pattern.exprs.isEmpty()) {
			return getTokenFromAst(pattern.exprs.get(0));
		} else if (ast instanceof TuplePattern pattern && !pattern.exprs.isEmpty()) {
			return getTokenFromAst(pattern.exprs.get(0));
		} else if (ast instanceof ObjectPattern pattern && !pattern.object.isEmpty()) {
			var first = pattern.object.keySet().iterator().next();
			return first.value;
		} else if (ast instanceof MatchCase casse) {
			return getTokenFromAst(casse.pattern);
		} else if (ast instanceof Array array) {
			return array.start;
		}
		// Fallback in case we can't extract a token
		return new Token(TokenKind.ERR, "unknown");
	}

	private CheckedNode inferTuple(Tuple tuple) {
		List<Type> types = new ArrayList<>();
		for (var node : tuple.values) {
			types.add(infer(node).type);
		}

		var name = "Tuple";
		if (sumTypes.containsKey(name)) {
			var t = sumTypes.get(name);
			t.arity = Math.max(types.size(), t.arity);
			sumTypes.put(name, t);
		} else {
			var t = new JFunctionObject(name, types.size());
			sumTypes.put(name, t);
		}

		return new CheckedNode(new TupleType(types), tuple);
	}

	private CheckedNode inferIdentifier(Identifier id) {
		String name = id.value.text;

		// Check if it's a constructor (starts with uppercase letter)
		if (Character.isUpperCase(name.charAt(0))) {
			if (!sumTypes.containsKey(name)) {
				var t = new JFunctionObject(name, 0);
				sumTypes.put(name, t);
			}
			id.sum = true;
			// This is a variant constructor or named type
			return new CheckedNode(new NamedType(id.value), id);
		}

		// Check if it's a function
		if (functionTable.containsKey(name)) {
			// If the function hasn't been type-checked yet, do it now
			if (!checkedFunctions.get(name)) {
				RFunction func = functionTable.get(name);
				var old = localSymbolTable;
				localSymbolTable = new HashMap<>();
				inferFunction(func);
				localSymbolTable = old;
			}

			// Return the function type
			return new CheckedNode(functionTypes.get(name), id);
		}

		// Otherwise check if it's a local variable
		if (typeEnv.containsKey(name)) {
			return new CheckedNode(typeEnv.get(name), id);
		}

		if (localSymbolTable.containsKey(name)) {
			return new CheckedNode(localSymbolTable.get(name), id);
		}

		Errors.reportTypeCheckError(id.value, "Usage of an undefined variable or function `" + name + "`");
		System.exit(0);
		return null;
	}

	private CheckedNode inferBinOp(BinOp binOp) {
		CheckedNode leftChecked = infer(binOp.lhs);
		CheckedNode rightChecked = infer(binOp.rhs);

		Type leftType = leftChecked.type;
		Type rightType = rightChecked.type;

		TokenKind op = binOp.op.kind;

		// For string concatenation
		if (op == TokenKind.STR_CONCAT_OPERATOR) {
			StringType stringType = new StringType();

			try {
				// Force both operands to be strings, no exceptions
				enforceStringType(leftType, binOp.lhs);
				enforceStringType(rightType, binOp.rhs);

				// Special handling for operands that are identifiers
				if (binOp.lhs instanceof Identifier) {
					String varName = ((Identifier) binOp.lhs).value.text;
					// Force this variable to be a string in our environment
					typeEnv.put(varName, stringType);

					// Also try to find any type variables that might be related to this variable
					// and constrain them too
					for (Map.Entry<TypeVariable, Type> entry : substitutions.entrySet()) {
						if (entry.getValue() instanceof TypeVariable
										&& typeEnv.containsValue(entry.getValue())
										&& typeEnv.get(varName) == entry.getValue()) {
							substitutions.put(entry.getKey(), stringType);
						}
					}
				}

				// Same for right operand
				if (binOp.rhs instanceof Identifier) {
					String varName = ((Identifier) binOp.rhs).value.text;
					typeEnv.put(varName, stringType);
					for (Map.Entry<TypeVariable, Type> entry : substitutions.entrySet()) {
						if (entry.getValue() instanceof TypeVariable
										&& typeEnv.containsValue(entry.getValue())
										&& typeEnv.get(varName) == entry.getValue()) {
							substitutions.put(entry.getKey(), stringType);
						}
					}
				}

				// Apply substitutions aggressively
				applyAllSubstitutions();

			} catch (TypeMismatchError e) {
				Errors.reportTypeCheckError(binOp.op, "Type mismatch in string concatenation: " + e.getMessage());
				System.exit(0);
			}

			return new CheckedNode(new StringType(), binOp);
		}

		// For numerical operations
		if (op == TokenKind.ADDITIVE_OPERATOR || op == TokenKind.MULTPLICATIVE_OPERATOR) {
			NumberType numberType = new NumberType();

			try {
				// Force both operands to be numbers
				unify(leftType, numberType, binOp.lhs);
				unify(rightType, numberType, binOp.rhs);

				// Apply substitutions aggressively
				applyAllSubstitutions();
			} catch (TypeMismatchError e) {
				Errors.reportTypeCheckError(binOp.op, "Type mismatch in numeric operation: " + e.getMessage());
				System.exit(0);
			}

			return new CheckedNode(new NumberType(), binOp);
		}

		// For comparisons
		if (op == TokenKind.BOOLEAN_OPERATOR) {
			try {
				unify(leftType, rightType, binOp);
				applyAllSubstitutions();
			} catch (TypeMismatchError e) {
				Errors.reportTypeCheckError(binOp.op, "Type mismatch in comparison: " + e.getMessage());
				System.exit(0);
			}
			return new CheckedNode(new BooleanType(), binOp);
		}

		Errors.reportTypeCheckError(binOp.op, "Unsupported binary operation: " + binOp.op.text);
		System.exit(0);
		return null;
	}

	// Helper method to propagate type constraints to all related type variables
	private void propagateTypeConstraint(Type concreteType) {
		// Create a list of type variables that need to be constrained
		List<TypeVariable> variablesToConstrain = new ArrayList<>();

		// Find all type variables in the environment that should be constrained
		for (Map.Entry<String, Type> entry : typeEnv.entrySet()) {
			findTypeVariables(entry.getValue(), variablesToConstrain);
		}

		// Find all type variables in function types
		for (Map.Entry<String, Type> entry : functionTypes.entrySet()) {
			findTypeVariables(entry.getValue(), variablesToConstrain);
		}

		// Apply the constraint to all found type variables
		for (TypeVariable tv : variablesToConstrain) {
			if (substitutions.containsKey(tv)) {
				Type currentType = substitutions.get(tv);

				// If the current substitution is a primitive type or already equals our concrete type, skip
				if (currentType.equals(concreteType) || isPrimitiveType(currentType)) {
					continue;
				}

				// If we're trying to constrain a type variable that's already constrained differently,
				// we need to make sure it's compatible
				if (currentType instanceof TypeVariable) {
					// It's still just a type variable, so we can constrain it
					substitutions.put(tv, concreteType);
				}
			} else {
				// No existing constraint, so add it
				substitutions.put(tv, concreteType);
			}
		}

		// Apply substitutions to make sure constraints propagate
		applyAllSubstitutions();
	}

	// Helper method to collect all type variables in a type
	private void findTypeVariables(Type type, List<TypeVariable> variables) {
		if (type instanceof TypeVariable) {
			if (!variables.contains((TypeVariable) type)) {
				variables.add((TypeVariable) type);
			}
		} else if (type instanceof FunctionType) {
			FunctionType ft = (FunctionType) type;
			for (Type argType : ft.args) {
				findTypeVariables(argType, variables);
			}
			findTypeVariables(ft.type, variables);
		}
	}

	// Helper to check if a type is a primitive type
	private boolean isPrimitiveType(Type type) {
		return type instanceof NumberType
						|| type instanceof StringType
						|| type instanceof BooleanType
						|| type instanceof UnitType;
	}

	private CheckedNode inferFunction(RFunction func) {
		String funcName = func.name.text;

		// Put bool values on the symbol table 
		typeEnv.put("true", new BooleanType());
		typeEnv.put("false", new BooleanType());

		// If we've already checked this function, return its type
		if (checkedFunctions.containsKey(funcName) && checkedFunctions.get(funcName)) {
			return new CheckedNode(functionTypes.get(funcName), func);
		}

		// Save current environment before processing this function
		Map<String, Type> savedEnv = new HashMap<>(typeEnv);
		Map<TypeVariable, Type> savedSubstitutions = new HashMap<>(substitutions);
		Map<String, Type> savedLocalSymbolTable = new HashMap<>(localSymbolTable);

		// Mark this function as being checked to handle recursive functions
		checkedFunctions.put(funcName, true);

		// For top-level function checking, we want clean environments
		// For nested function checking (during expression evaluation), preserve the environment
		boolean isCalledFromProcess = savedEnv.isEmpty();
		if (isCalledFromProcess) {
			// If this is a top-level function check (called from process()), clear the environment
			typeEnv.clear();
			substitutions.clear();
			localSymbolTable.clear();
		}

		// Initialize with skeleton type variables from registration
		FunctionType funcType = (FunctionType) functionTypes.get(funcName);
		List<Type> argTypes = new ArrayList<>();

		// Create fresh type variables for each argument
		for (int i = 0; i < func.args.size(); i++) {
			Arg arg = func.args.get(i);
			Type argType;
			if (arg.type != null) {
				argType = arg.type;
			} else {
				argType = freshTypeVar(arg.name);
			}
			typeEnv.put(arg.name.text, argType);
			argTypes.add(argType);
		}

		var names = new ArrayList<String>();
		if (!func.where.isEmpty()) {
			// First, register all where functions in the function table to allow recursive calls
			for (var f : func.where) {
				var fx = (RFunction) f;
				names.add(fx.name.text);
				functionTable.put(fx.name.text, fx);

				// Register a skeleton function type for each where function
				String whereFuncName = fx.name.text;
				List<Type> whereArgTypes = new ArrayList<>();
				for (Arg arg : fx.args) {
					Type whereArgType = arg.type != null ? arg.type : freshTypeVar(arg.name);
					whereArgTypes.add(whereArgType);
				}
				Type whereReturnType = fx.type != null ? fx.type : freshTypeVar(fx.name);
				functionTypes.put(whereFuncName, new FunctionType(whereArgTypes, whereReturnType));
				checkedFunctions.put(whereFuncName, false);
			}

			// Then, infer types for each where function
			for (var f : func.where) {
				var fx = (RFunction) f;
				inferFunction(fx);
			}
		}

		// Special handling for when expressions in function bodies
		boolean hasWhenExpression = func.body instanceof When;

		// Infer the type of the function body
		CheckedNode bodyChecked = infer(func.body);

		// Process any recursive calls or other type constraints
		findAndEnforceTypeConstraints(func.body);

		// Apply substitutions to all types
		applyAllSubstitutions();

		// Handle the function's return type, which may depend on its body
		Type returnType = handleFunctionReturnType(func, bodyChecked, argTypes);

		// Detect operations like string concatenation in the function body
		detectAndHandleOperations(func.body, argTypes);

		// Apply substitutions aggressively again
		applyAllSubstitutions();

		names.forEach((name) -> checkedFunctions.remove(name));
		names.forEach((name) -> functionTable.remove(name));
		names.forEach((name) -> functionTypes.remove(name));

		// Update all argument types with their most specialized versions
		for (int i = 0; i < argTypes.size(); i++) {
			argTypes.set(i, applySubstitutions(argTypes.get(i)));
		}

		// For pattern matching functions, ensure proper type propagation
		if (hasWhenExpression && func.body instanceof When) {
			When when = (When) func.body;
			if (when.match instanceof Identifier) {
				String paramName = ((Identifier) when.match).value.text;
				// Find the parameter index
				int paramIndex = -1;
				for (int i = 0; i < func.args.size(); i++) {
					if (func.args.get(i).name.text.equals(paramName)) {
						paramIndex = i;
						break;
					}
				}

				if (paramIndex >= 0) {
					// Get the most refined match type after all inferences
					CheckedNode matchChecked = infer(when.match);
					Type matchType = applySubstitutions(matchChecked.type);

					// Update the argument type
					argTypes.set(paramIndex, matchType);

					// Also check for binding patterns returning the same variable
					for (MatchCase casse : when.cases) {
						if (casse.pattern instanceof BindPattern
										&& casse.body instanceof Identifier
										&& ((Identifier) casse.body).value.text.equals(
														((BindPattern) casse.pattern).name.value.text)) {
							// This is a case like "is b -> b", so ensure return type includes match type
							if (returnType instanceof UnionType) {
								UnionType ut = (UnionType) returnType;
								List<Type> types = new ArrayList<>(ut.types);
								boolean hasMatchType = false;
								for (Type t : types) {
									if (t.equals(matchType)) {
										hasMatchType = true;
										break;
									}
								}
								if (!hasMatchType) {
									types.add(matchType);
									returnType = new UnionType(types);
								}
							} else if (!returnType.equals(matchType)) {
								// Create a union of current return type and match type
								returnType = new UnionType(Arrays.asList(returnType, matchType));
							}
							break;
						}
					}
				}
			}
		}

		// Apply substitutions to get the final types
		Type substitutedReturnType = applySubstitutions(returnType);

		// Create the final function type
		FunctionType finalFuncType = new FunctionType(argTypes, substitutedReturnType);

		// Present the final type with aliases where appropriate
		finalFuncType = (FunctionType) presentFinalType(finalFuncType);

		// Update function type in our registry
		functionTypes.put(funcName, finalFuncType);

		// Restore the previous environment
		if (!isCalledFromProcess) {
			typeEnv = savedEnv;
			substitutions = savedSubstitutions;
			localSymbolTable = savedLocalSymbolTable;
		}

		return new CheckedNode(finalFuncType, func);
	}

	// New method to identify and constrain operations for all types
	private void findAndEnforceTypeConstraints(Ast node) {
		if (node instanceof BinOp) {
			BinOp binOp = (BinOp) node;
			TokenKind op = binOp.op.kind;

			// Get concrete type based on operation
			Type concreteType = null;
			if (op == TokenKind.MULTPLICATIVE_OPERATOR || op == TokenKind.ADDITIVE_OPERATOR) {
				concreteType = new NumberType();
			} else if (op == TokenKind.STR_CONCAT_OPERATOR) {
				concreteType = new StringType();
			} else if (op == TokenKind.BOOLEAN_OPERATOR) {
				concreteType = new BooleanType();
			}

			if (concreteType != null) {
				// Find all identifiers in both operands
				List<Identifier> identifiers = new ArrayList<>();
				findIdentifiers(binOp.lhs, identifiers);
				findIdentifiers(binOp.rhs, identifiers);

				// Constrain ALL identifiers involved to be of the concrete type
				for (Identifier id : identifiers) {
					String name = id.value.text;
					if (typeEnv.containsKey(name)) {
						Type currentType = typeEnv.get(name);
						// Directly set to concrete type
						typeEnv.put(name, concreteType);

						// If it was a type variable, update substitutions too
						if (currentType instanceof TypeVariable) {
							substitutions.put((TypeVariable) currentType, concreteType);
						}
					}
				}
			}

			// Recursively check both operands
			findAndEnforceTypeConstraints(binOp.lhs);
			findAndEnforceTypeConstraints(binOp.rhs);
		} else if (node instanceof Call) {
			Call call = (Call) node;
			// Check the function being called
			findAndEnforceTypeConstraints(call.expr);
			// Check all arguments
			for (Ast arg : call.params) {
				findAndEnforceTypeConstraints(arg);
			}
		} else if (node instanceof IfElse) {
			IfElse ifElse = (IfElse) node;
			findAndEnforceTypeConstraints(ifElse.cond);
			findAndEnforceTypeConstraints(ifElse.then);
			findAndEnforceTypeConstraints(ifElse.elze);
		}
	}

	// Handle function return type checking and unification
	private Type handleFunctionReturnType(RFunction func, CheckedNode bodyChecked, List<Type> argTypes) {
		Type returnType = bodyChecked.type;

		// Special handling for when expressions in function bodies
		if (func.body instanceof When) {
			// Allow the match expression to have a union type
			When when = (When) func.body;
			CheckedNode matchChecked = infer(when.match);
			Type matchType = applySubstitutions(matchChecked.type);

			// Collect concrete pattern types
			List<Type> concretePatternTypes = new ArrayList<>();
			for (MatchCase casse : when.cases) {
				if (casse.pattern instanceof ExpressionPattern) {
					ExpressionPattern exprPattern = (ExpressionPattern) casse.pattern;
					CheckedNode patternChecked = infer(exprPattern.expr);
					if (!(patternChecked.type instanceof TypeVariable)) {
						concretePatternTypes.add(patternChecked.type);
					}
				}
			}

			// If we have concrete pattern types, try to constrain the match expression
			if (!concretePatternTypes.isEmpty()) {
				Type concreteType = concretePatternTypes.size() == 1
								? concretePatternTypes.get(0) : new UnionType(concretePatternTypes);

				// If the match expression is a parameter, update its type to the concrete type
				if (when.match instanceof Identifier identifier) {
					String paramName = identifier.value.text;
					for (int i = 0; i < func.args.size(); i++) {
						Arg arg = func.args.get(i);
						if (arg.name.text.equals(paramName)) {
							// Try to unify with concrete type
							try {
								Type argType = argTypes.get(i);
								if (argType instanceof TypeVariable) {
									substituteTypeVar((TypeVariable) argType, concreteType);
									argTypes.set(i, concreteType);
								}
							} catch (Exception e) {
								// Keep the original type if unification fails
							}
							break;
						}
					}
				}
			}
		}

		// If the function body is a call to a function that returns an AppType
		if (func.body instanceof Call) {
			Call call = (Call) func.body;

			// First apply substitutions to ensure we have up-to-date types
			applyAllSubstitutions();

			if (call.expr instanceof Identifier) {
				String calleeName = ((Identifier) call.expr).value.text;

				// If this is a call to a known function (not a constructor)
				if (functionTable.containsKey(calleeName)) {
					Type calleeType = functionTypes.get(calleeName);
					if (calleeType instanceof FunctionType) {
						FunctionType ft = (FunctionType) calleeType;
						Type calleeReturnType = applySubstitutions(ft.type);

						// If the callee returns an AppType (variant)
						if (calleeReturnType instanceof AppType) {
							AppType appType = (AppType) calleeReturnType;

							// Check the arguments to the callee function
							for (int i = 0; i < call.params.size(); i++) {
								// Process each argument to the function
								Ast param = call.params.get(i);
								CheckedNode paramChecked = infer(param);
								Type paramType = paramChecked.type;

								// If the argument is a concrete primitive type, use it in the result
								if (isPrimitiveType(paramType)) {
									// Create a new AppType with this concrete type
									List<Type> newParams = new ArrayList<>();
									for (int j = 0; j < appType.args.size(); j++) {
										if (appType.args.get(j) instanceof TypeVariable) {
											newParams.add(paramType);
										} else {
											newParams.add(appType.args.get(j));
										}
									}
									// Replace the return type with the new AppType
									returnType = new AppType(appType.cons, newParams);
									break;
								}
							}
						}
					}
				}
			}
		}

		// Rest of the method to handle return type annotation
		if (func.type != null) {
			try {
				unify(func.type, returnType, func.body);
			} catch (TypeMismatchError e) {
				Errors.reportTypeCheckError(getTokenFromAst(func.body), e.getMessage());
			}
		}

		// Apply substitutions again to ensure all types are up-to-date
		applyAllSubstitutions();
		returnType = applySubstitutions(returnType);

		// Return the final type
		return returnType;
	}

	// New helper method to recursively analyze the function body and properly constrain operations
	private void detectAndHandleOperations(Ast node, List<Type> argTypes) {
		if (node instanceof BinOp) {
			BinOp binOp = (BinOp) node;
			TokenKind op = binOp.op.kind;

			// Find identifiers used in the operation
			List<Identifier> identifiers = new ArrayList<>();
			findIdentifiers(binOp.lhs, identifiers);
			findIdentifiers(binOp.rhs, identifiers);

			// For string concatenation, force all related identifiers to be strings
			if (op == TokenKind.STR_CONCAT_OPERATOR) {
				StringType stringType = new StringType();
				enforceTypeToIdentifiers(identifiers, stringType, argTypes);
			} // For numeric operations, force all related identifiers to be numbers
			else if (op == TokenKind.ADDITIVE_OPERATOR || op == TokenKind.MULTPLICATIVE_OPERATOR) {
				NumberType numberType = new NumberType();
				enforceTypeToIdentifiers(identifiers, numberType, argTypes);
			} // For boolean operations, force all related identifiers to be booleans
			else if (op == TokenKind.BOOLEAN_OPERATOR) {
				BooleanType booleanType = new BooleanType();
				enforceTypeToIdentifiers(identifiers, booleanType, argTypes);
			}
		}

		// Recursively check subexpressions
		if (node instanceof BinOp) {
			detectAndHandleOperations(((BinOp) node).lhs, argTypes);
			detectAndHandleOperations(((BinOp) node).rhs, argTypes);
		} else if (node instanceof Call) {
			Call call = (Call) node;
			detectAndHandleOperations(call.expr, argTypes);
			for (Ast arg : call.params) {
				detectAndHandleOperations(arg, argTypes);
			}
		} else if (node instanceof IfElse) {
			IfElse ifElse = (IfElse) node;
			detectAndHandleOperations(ifElse.cond, argTypes);
			detectAndHandleOperations(ifElse.then, argTypes);
			detectAndHandleOperations(ifElse.elze, argTypes);
		}
	}

	// Helper method to enforce a concrete type to a list of identifiers
	private void enforceTypeToIdentifiers(List<Identifier> identifiers, Type concreteType, List<Type> argTypes) {
		for (Identifier id : identifiers) {
			String name = id.value.text;
			if (typeEnv.containsKey(name)) {
				Type currentType = typeEnv.get(name);
				// Directly set to concrete type
				typeEnv.put(name, concreteType);

				// If it was a type variable, update substitutions too
				if (currentType instanceof TypeVariable) {
					substitutions.put((TypeVariable) currentType, concreteType);
				}
			}
		}

		// Handle propagation to function arguments
		for (int i = 0; i < argTypes.size(); i++) {
			if (argTypes.get(i) instanceof TypeVariable) {
				for (Identifier id : identifiers) {
					if (typeEnv.containsKey(id.value.text)
									&& typeEnv.get(id.value.text).equals(argTypes.get(i))) {
						argTypes.set(i, concreteType);
					}
				}
			}
		}
	}

	// Helper to find all identifiers in an expression
	private void findIdentifiers(Ast node, List<Identifier> identifiers) {
		if (node instanceof Identifier) {
			identifiers.add((Identifier) node);
		} else if (node instanceof BinOp) {
			BinOp binOp = (BinOp) node;
			findIdentifiers(binOp.lhs, identifiers);
			findIdentifiers(binOp.rhs, identifiers);
		} else if (node instanceof Call) {
			Call call = (Call) node;
			findIdentifiers(call.expr, identifiers);
			for (Ast arg : call.params) {
				findIdentifiers(arg, identifiers);
			}
		}
		// Add other node types as needed
	}

	private CheckedNode inferClosure(RClosure closure) {
		// Save old environment and substitutions
		Map<String, Type> oldEnv = new HashMap<>(typeEnv);
		Map<TypeVariable, Type> oldSubstitutions = new HashMap<>(substitutions);

		// Process arguments
		List<Type> argTypes = new ArrayList<>();
		for (Arg arg : closure.args) {
			Type argType;
			if (arg.type != null) {
				argType = arg.type;
			} else {
				argType = freshTypeVar(arg.name);
			}
			typeEnv.put(arg.name.text, argType);
			argTypes.add(argType);
		}

		// Process the body
		CheckedNode bodyChecked = infer(closure.body);

		// Apply more aggressive constraint detection for all types
		findAndEnforceTypeConstraints(closure.body);

		// Apply substitutions inside the closure
		applyAllSubstitutions();

		Type returnType = applySubstitutions(bodyChecked.type);

		// Get final types after substitutions
		List<Type> substitutedArgTypes = new ArrayList<>();
		for (Type argType : argTypes) {
			substitutedArgTypes.add(applySubstitutions(argType));
		}
		Type substitutedReturnType = applySubstitutions(returnType);

		// IMPROVED CONSTRAINT MERGING:
		// Create merged substitution map
		Map<TypeVariable, Type> mergedSubstitutions = new HashMap<>(oldSubstitutions);

		// For variables in the old environment that we've learned more about
		for (Map.Entry<String, Type> entry : oldEnv.entrySet()) {
			String name = entry.getKey();
			Type oldType = entry.getValue();

			if (typeEnv.containsKey(name) && !typeEnv.get(name).equals(oldType)) {
				// If the old type was a type variable, update its substitution
				if (oldType instanceof TypeVariable) {
					Type newType = typeEnv.get(name);
					// Propagate all types, not just primitives
					mergedSubstitutions.put((TypeVariable) oldType, newType);
				}
			}
		}

		// For all substitutions we've made in this scope
		for (Map.Entry<TypeVariable, Type> entry : substitutions.entrySet()) {
			TypeVariable tv = entry.getKey();
			Type newType = entry.getValue();

			// If this type variable is used in the parent scope
			boolean usedInParent = false;
			for (Type type : oldEnv.values()) {
				if (occursIn(tv, type)) {
					usedInParent = true;
					break;
				}
			}

			// If this type variable is relevant to the parent scope
			if (usedInParent || argTypes.contains(tv)) {
				// Propagate ALL type constraints
				mergedSubstitutions.put(tv, newType);
			}
		}

		// Restore environment with updated substitutions
		typeEnv = oldEnv;
		substitutions = mergedSubstitutions;

		// Create the final function type
		FunctionType functionType = new FunctionType(substitutedArgTypes, substitutedReturnType);

		return new CheckedNode(functionType, closure);
	}

	private CheckedNode inferCall(Call call) {
		// IMPORTANT - Do not modify this code block
		if (call.expr instanceof Identifier id
						&& !functionTable.containsKey(id.value.text)
						&& Character.isUpperCase(id.value.text.charAt(0))) {
			// This is a variant constructor application
			String name = id.value.text;
			List<Type> argTypes = new ArrayList<>();

			// Infer types for all arguments
			for (Ast arg : call.params) {
				CheckedNode argChecked = infer(arg);
				argTypes.add(argChecked.type);
			}

			if (sumTypes.containsKey(name)) {
				var t = sumTypes.get(name);
				t.arity = Math.max(call.params.size(), t.arity);
				sumTypes.put(name, t);
			} else {
				var t = new JFunctionObject(name, call.params.size());
				sumTypes.put(name, t);
			}

			call.sum = true;
			// Create an AppType with the constructor name and argument types
			// Simple case: Single argument constructor (e.g., Some a)
			return new CheckedNode(new AppType(id.value, argTypes), call);
		}

		// Store the original substitutions map to restore it after call processing
		Map<TypeVariable, Type> originalSubstitutions = new HashMap<>(substitutions);
		
		// Regular function call processing
		CheckedNode funcChecked = infer(call.expr);
		Type funcType = funcChecked.type;
		
		// Make a deep copy of the function type to prevent modifying the original
		Type originalFuncType = funcType;
		if (funcType instanceof FunctionType) {
			funcType = ((FunctionType) funcType).clone();
		}

		// Check that the function type is a function
		if (!(funcType instanceof FunctionType)) {
			// Handle functions that return closures (auto-currying)
			if (funcType instanceof FunctionType ft && ft.args.isEmpty() && ft.type instanceof FunctionType) {
				funcType = ft.type;
			} else {
				Errors.reportTypeCheckError(getTokenFromAst(call.expr),
								"Cannot call a non-function type: " + funcType);
				System.exit(0);
				return null;
			}
		}

		FunctionType ft = (FunctionType) funcType;
		Type resultType = ft;

		// Handle no-argument function calls with empty parentheses
		if (call.params.size() == 0 && ft.args.size() == 0) {
			// Restore original substitutions
			substitutions = originalSubstitutions;
			return new CheckedNode(ft.type, call);
		}

		// Handle function call with Unit argument to a no-argument function
		if (call.params.size() == 1 && ft.args.size() == 0) {
		}

		// Process each argument
		for (int i = 0; i < call.params.size(); i++) {
			// We need to ensure we have a function type for each argument
			if (!(resultType instanceof FunctionType)) {
				// Check if this is a multi-argument call on a curried function chain
				if (i > 0) {
					// Instead of error, we'll create a new call with remaining arguments
					Ast funcExpr = new Call(call.expr, call.params.subList(0, i));
					List<Ast> remainingArgs = call.params.subList(i, call.params.size());
					
					// Restore original substitutions
					substitutions = originalSubstitutions;
					return inferCall(new Call(funcExpr, remainingArgs));
				} else {
					// Restore original substitutions
					substitutions = originalSubstitutions;
					Errors.reportTypeCheckError(getTokenFromAst(call.expr),
									"Too many arguments provided to function call");
					System.exit(0);
					return null;
				}
			}

			ft = (FunctionType) resultType;
			Ast argAst = call.params.get(i);
			CheckedNode argChecked = infer(argAst);
			Type argType = argChecked.type;

			try {
				unify(ft.args.get(0), argType, argAst);
				// We only apply substitutions locally, to the copied function type
				// Don't call applyAllSubstitutions() here, as it would affect the global state
			} catch (TypeMismatchError e) {
				// Restore original substitutions
				substitutions = originalSubstitutions;
				Errors.reportTypeCheckError(getTokenFromAst(argAst),
								"Type mismatch in function call: " + e.getMessage());
				System.exit(0);
			}

			// Move to the next parameter type
			if (ft.args.size() > 1) {
				List<Type> remainingArgs = new ArrayList<>(ft.args.subList(1, ft.args.size()));
				resultType = new FunctionType(remainingArgs, ft.type);
			} else {
				resultType = ft.type;
			}
		}

		// Apply substitutions but only to our result type, not to the entire type environment
		resultType = applySubstitutions(resultType);
		
		// Save any new substitutions that should be kept (exclude function type variables)
		Map<TypeVariable, Type> newSubstitutions = new HashMap<>();
		for (Map.Entry<TypeVariable, Type> entry : substitutions.entrySet()) {
			if (!originalSubstitutions.containsKey(entry.getKey())) {
				// Only keep substitutions for type variables not related to function parameters
				boolean isFunctionTypeVar = false;
				if (originalFuncType instanceof FunctionType) {
					FunctionType ofType = (FunctionType) originalFuncType;
					for (Type argType : ofType.args) {
						if (argType instanceof TypeVariable && 
								((TypeVariable) argType).name.text.equals(entry.getKey().name.text)) {
							isFunctionTypeVar = true;
							break;
						}
					}
				}
				if (!isFunctionTypeVar) {
					newSubstitutions.put(entry.getKey(), entry.getValue());
				}
			}
		}
		
		// Restore original substitutions, but add any new ones that should be kept
		substitutions = originalSubstitutions;
		substitutions.putAll(newSubstitutions);
		
		return new CheckedNode(resultType, call);
	}

	private void applyAllSubstitutions() {
		boolean changed;
		do {
			changed = false;

			// Apply substitutions to all type variables in substitutions
			Map<TypeVariable, Type> newSubstitutions = new HashMap<>();
			for (Map.Entry<TypeVariable, Type> entry : substitutions.entrySet()) {
				Type newType = applySubstitutions(entry.getValue());
				if (!newType.equals(entry.getValue())) {
					changed = true;
				}
				newSubstitutions.put(entry.getKey(), newType);
			}
			substitutions = newSubstitutions;

			// Apply substitutions to all variables in environment
			Map<String, Type> newEnv = new HashMap<>();
			for (Map.Entry<String, Type> entry : typeEnv.entrySet()) {
				Type newType = applySubstitutions(entry.getValue());
				if (!newType.equals(entry.getValue())) {
					changed = true;
				}
				newEnv.put(entry.getKey(), newType);
			}
			typeEnv = newEnv;

			// Apply substitutions to function types
			Map<String, Type> newFunctionTypes = new HashMap<>();
			for (Map.Entry<String, Type> entry : functionTypes.entrySet()) {
				Type newType = applySubstitutions(entry.getValue());
				if (!newType.equals(entry.getValue())) {
					changed = true;
				}
				newFunctionTypes.put(entry.getKey(), newType);
			}
			functionTypes = newFunctionTypes;

		} while (changed);
	}

	// Custom exception for type mismatch errors
	private static class TypeMismatchError extends Exception {

		public TypeMismatchError(String message) {
			super(message);
		}
	}

	// Unification algorithm for type inference
	private void unify(Type t1, Type t2, Ast node) throws TypeMismatchError {
		// Apply existing substitutions first
		t1 = applySubstitutions(t1);
		t2 = applySubstitutions(t2);

		if (t1.equals(t2)) {
			return;
		}

		// Handle TypeVariable with UnionType more permissively
		if (t1 instanceof TypeVariable && t2 instanceof UnionType) {
			substituteTypeVar((TypeVariable) t1, t2);
			return;
		}

		if (t2 instanceof TypeVariable && t1 instanceof UnionType) {
			substituteTypeVar((TypeVariable) t2, t1);
			return;
		}

		// Handle NamedType unification
		if (t1 instanceof NamedType && t2 instanceof NamedType) {
			NamedType nt1 = (NamedType) t1;
			NamedType nt2 = (NamedType) t2;

			// Named types must match exactly by name
			if (!nt1.name.text.equals(nt2.name.text)) {
				throw new TypeMismatchError(nt1 + " is not compatible with " + nt2);
			}
			return;
		}

		// Special case: NamedType can't unify with concrete types
		if ((t1 instanceof NamedType && isPrimitiveType(t2))
						|| (t2 instanceof NamedType && isPrimitiveType(t1))) {
			throw new TypeMismatchError("Named type ("
							+ (t1 instanceof NamedType ? t1 : t2)
							+ ") cannot be unified with primitive type ("
							+ (isPrimitiveType(t1) ? t1 : t2) + ")");
		}

		// Handle TypeVariable with NamedType (only TypeVariable can be substituted)
		if (t1 instanceof TypeVariable && t2 instanceof NamedType) {
			substituteTypeVar((TypeVariable) t1, t2);
			return;
		}

		if (t2 instanceof TypeVariable && t1 instanceof NamedType) {
			substituteTypeVar((TypeVariable) t2, t1);
			return;
		}

		// Handle UnionType specially
		if (t1 instanceof UnionType && t2 instanceof UnionType) {
			UnionType ut1 = (UnionType) t1;
			UnionType ut2 = (UnionType) t2;

			// For union types to match, they must contain the same set of types
			if (ut1.types.size() != ut2.types.size()) {
				throw new TypeMismatchError("Union types don't match");
			}

			// Check if each type in ut1 can be unified with some type in ut2
			for (Type type1 : ut1.types) {
				boolean foundMatch = false;
				for (Type type2 : ut2.types) {
					try {
						unify(type1, type2, node);
						foundMatch = true;
						break;
					} catch (TypeMismatchError e) {
						// Continue trying other types
					}
				}
				if (!foundMatch) {
					throw new TypeMismatchError("Union type " + t1 + " cannot be unified with " + t2);
				}
			}
			return;
		}

		// Special case for primitive types and union types
		if (isPrimitiveType(t1) && t2 instanceof UnionType) {
			UnionType union = (UnionType) t2;
			// Check if t1 matches any of the union's types
			for (Type unionType : union.types) {
				try {
					if (isPrimitiveType(unionType) && t1.getClass() == unionType.getClass()) {
						return; // Types match
					}
				} catch (Exception e) {
					// Continue trying other types
				}
			}
			throw new TypeMismatchError(t1 + " doesn't match any type in union " + t2);
		}

		if (isPrimitiveType(t2) && t1 instanceof UnionType) {
			UnionType union = (UnionType) t1;
			// Check if t2 matches any of the union's types
			for (Type unionType : union.types) {
				try {
					if (isPrimitiveType(unionType) && t2.getClass() == unionType.getClass()) {
						return; // Types match
					}
				} catch (Exception e) {
					// Continue trying other types
				}
			}
			throw new TypeMismatchError(t2 + " doesn't match any type in union " + t1);
		}

		// If one is a union type and the other is not
		if (t1 instanceof UnionType) {
			UnionType union = (UnionType) t1;
			// Check if t2 matches any of the union's types
			for (Type unionType : union.types) {
				try {
					unify(unionType, t2, node);
					return; // If we find a match, we're done
				} catch (TypeMismatchError e) {
					// Continue trying other types
				}
			}
			throw new TypeMismatchError(t2 + " doesn't match any type in union " + t1);
		}

		if (t2 instanceof UnionType) {
			UnionType union = (UnionType) t2;
			// Check if t1 matches any of the union's types
			for (Type unionType : union.types) {
				try {
					unify(t1, unionType, node);
					return; // If we find a match, we're done
				} catch (TypeMismatchError e) {
					// Continue trying other types
				}
			}
			throw new TypeMismatchError(t1 + " doesn't match any type in union " + t2);
		}

		if (t1 instanceof TypeVariable) {
			substituteTypeVar((TypeVariable) t1, t2);
			return;
		}

		if (t2 instanceof TypeVariable) {
			substituteTypeVar((TypeVariable) t2, t1);
			return;
		}

		// AppType unification
		if (t1 instanceof AppType && t2 instanceof AppType) {
			AppType at1 = (AppType) t1;
			AppType at2 = (AppType) t2;

			// Check constructor names match
			if (!at1.cons.text.equals(at2.cons.text)) {
				throw new TypeMismatchError("Constructor names don't match: " + at1.cons.text + " vs " + at2.cons.text);
			}

			// Check number of parameters match
			if (at1.args.size() != at2.args.size()) {
				throw new TypeMismatchError("Constructor " + at1.cons.text + " has different number of arguments");
			}

			// Unify each parameter type
			for (int i = 0; i < at1.args.size(); i++) {
				unify(at1.args.get(i), at2.args.get(i), node);
			}
			return;
		}

		if (t1 instanceof FunctionType && t2 instanceof FunctionType) {
			FunctionType ft1 = (FunctionType) t1;
			FunctionType ft2 = (FunctionType) t2;

			// Check if both functions have the same number of arguments
			if (ft1.args.size() != ft2.args.size()) {
				throw new TypeMismatchError("Function types have different arity");
			}

			// Unify each argument type
			for (int i = 0; i < ft1.args.size(); i++) {
				unify(ft1.args.get(i), ft2.args.get(i), node);
			}

			// Unify return types
			unify(ft1.type, ft2.type, node);
			return;
		}

		if (t1 instanceof ObjectType && t2 instanceof ObjectType) {
			ObjectType ot1 = (ObjectType) t1;
			ObjectType ot2 = (ObjectType) t2;

			// For structure typing, check that all fields in t1 exist in t2 with compatible types
			for (Map.Entry<Token, Type> entry : ot1.fields.entrySet()) {
				String fieldName = entry.getKey().text;
				Type fieldType1 = entry.getValue();

				// Find the same field in the second object
				boolean foundField = false;
				for (Map.Entry<Token, Type> entry2 : ot2.fields.entrySet()) {
					if (entry2.getKey().text.equals(fieldName)) {
						foundField = true;
						Type fieldType2 = entry2.getValue();

						// Unify the field types
						unify(fieldType1, fieldType2, node);
						break;
					}
				}

				if (!foundField) {
					throw new TypeMismatchError("Object type is missing field: " + fieldName);
				}
			}

			// Also check that all fields in t2 exist in t1
			for (Map.Entry<Token, Type> entry : ot2.fields.entrySet()) {
				String fieldName = entry.getKey().text;

				boolean foundField = false;
				for (Map.Entry<Token, Type> entry1 : ot1.fields.entrySet()) {
					if (entry1.getKey().text.equals(fieldName)) {
						foundField = true;
						break;
					}
				}

				if (!foundField) {
					throw new TypeMismatchError("Object type is missing field: " + fieldName);
				}
			}

			return;
		}

		throw new TypeMismatchError(t1 + " is not compatible with " + t2);
	}

	// Substitute a type variable with a concrete type
	private void substituteTypeVar(TypeVariable tv, Type type) {
		if (type instanceof TypeVariable && tv.equals(type)) {
			return;
		}

		// Check for circular references
		if (occursIn(tv, type)) {
			return;
		}

		// Only consider type aliases for user-defined variables
		boolean isAlias = tv.is_user_defined && typeAliases.containsKey(tv.name.text);

		if (isAlias) {
			Type aliasType = typeAliases.get(tv.name.text).type;
			try {
				unify(aliasType, type, null);
				return;
			} catch (TypeMismatchError e) {
				// Fall through to normal substitution
			}
		}

		// Special case: if we're given a union type, check if it contains concrete types
		// and prefer those over type variables when possible
		if (type instanceof UnionType unionType) {
			// Try to find a concrete type in the union
			Type concreteType = null;
			for (Type memberType : unionType.types) {
				if (!(memberType instanceof TypeVariable)) {
					concreteType = memberType;
					break;
				}
			}

			// If we found a concrete type, use it instead of the union
			if (concreteType != null && unionType.types.size() == 2) {
				// Check if the union is just (concrete | typevar)
				boolean onlyOneTypeVar = true;
				for (Type memberType : unionType.types) {
					if (memberType != concreteType && !(memberType instanceof TypeVariable)) {
						onlyOneTypeVar = false;
						break;
					}
				}

				// If the union is just one concrete type and type variables, use the concrete type
				if (onlyOneTypeVar) {
					type = concreteType;
				}
			}
		}

		// First, apply any existing substitutions to the replacement type
		Type substitutedType = applySubstitutions(type);

		// Update the global substitution map
		substitutions.put(tv, substitutedType);

		// Update other substitutions that might reference this type variable
		for (Map.Entry<TypeVariable, Type> entry : substitutions.entrySet()) {
			if (!entry.getKey().equals(tv)) {
				substitutions.put(entry.getKey(),
								substituteInType(entry.getValue(), tv, substitutedType));
			}
		}

		// Update the environment
		for (Map.Entry<String, Type> entry : typeEnv.entrySet()) {
			typeEnv.put(entry.getKey(),
							substituteInType(entry.getValue(), tv, substitutedType));
		}
	}

	// Apply all known substitutions to a type
	private Type applySubstitutions(Type type) {
		Type result = type;
		boolean changed;

		do {
			changed = false;

			// Handle NamedType - only resolve if it's a type alias
			if (result instanceof NamedType) {
				NamedType nt = (NamedType) result;
				if (typeAliases.containsKey(nt.name.text)) {
					result = typeAliases.get(nt.name.text).type;
					changed = true;
					continue;
				}
			}

			// Original code to handle substitutions
			if (result instanceof TypeVariable) {
				TypeVariable tv = (TypeVariable) result;
				if (substitutions.containsKey(tv)) {
					result = substitutions.get(tv);
					changed = true;
				}
			} else if (result instanceof FunctionType) {
				FunctionType ft = (FunctionType) result;
				List<Type> newArgs = new ArrayList<>();
				boolean argsChanged = false;

				for (Type argType : ft.args) {
					Type newArgType = applySubstitutions(argType);
					newArgs.add(newArgType);
					if (newArgType != argType) {
						argsChanged = true;
					}
				}

				Type newReturnType = applySubstitutions(ft.type);
				boolean returnChanged = newReturnType != ft.type;

				if (argsChanged || returnChanged) {
					result = new FunctionType(newArgs, newReturnType);
					changed = true;
				}
			} else if (result instanceof AppType) {
				AppType at = (AppType) result;
				List<Type> newArgs = new ArrayList<>();
				boolean argsChanged = false;

				for (Type argType : at.args) {
					Type newArgType = applySubstitutions(argType);
					newArgs.add(newArgType);
					if (newArgType != argType) {
						argsChanged = true;
					}
				}

				if (argsChanged) {
					result = new AppType(at.cons, newArgs);
					changed = true;
				}
			} else if (result instanceof UnionType) {
				UnionType ut = (UnionType) result;
				List<Type> newTypes = new ArrayList<>();
				boolean typesChanged = false;

				for (Type unionType : ut.types) {
					Type newType = applySubstitutions(unionType);
					newTypes.add(newType);
					if (newType != unionType) {
						typesChanged = true;
					}
				}

				if (typesChanged) {
					result = new UnionType(newTypes);
					changed = true;
				}
			} else if (result instanceof ObjectType) {
				ObjectType ot = (ObjectType) result;
				HashMap<Token, Type> newFields = new HashMap<>();
				boolean fieldsChanged = false;

				for (Map.Entry<Token, Type> entry : ot.fields.entrySet()) {
					Type newFieldType = applySubstitutions(entry.getValue());
					newFields.put(entry.getKey(), newFieldType);
					if (newFieldType != entry.getValue()) {
						fieldsChanged = true;
					}
				}

				if (fieldsChanged) {
					result = new ObjectType(newFields);
					changed = true;
				}
			}
		} while (changed);

		return result;
	}

	// Check if a type variable occurs in a type (occurs check)
	private boolean occursIn(TypeVariable tv, Type type) {
		// Add special case for NamedType
		if (type instanceof NamedType) {
			return false; // A TypeVariable can't occur in a NamedType
		}

		if (type instanceof TypeVariable) {
			return tv.equals(type);
		}
		if (type instanceof FunctionType) {
			FunctionType ft = (FunctionType) type;

			for (Type argType : ft.args) {
				if (occursIn(tv, argType)) {
					return true;
				}
			}

			return occursIn(tv, ft.type);
		}
		if (type instanceof AppType) {
			AppType at = (AppType) type;

			for (Type argType : at.args) {
				if (occursIn(tv, argType)) {
					return true;
				}
			}
		}
		if (type instanceof UnionType) {
			UnionType ut = (UnionType) type;

			for (Type unionType : ut.types) {
				if (occursIn(tv, unionType)) {
					return true;
				}
			}
		}
		if (type instanceof ObjectType) {
			ObjectType ot = (ObjectType) type;

			for (Type fieldType : ot.fields.values()) {
				if (occursIn(tv, fieldType)) {
					return true;
				}
			}
		}
		return false;
	}

	// Substitute a type variable in a type with another type
	private Type substituteInType(Type type, TypeVariable tv, Type replacement) {
		// Add special case for NamedType
		if (type instanceof NamedType) {
			// NamedType shouldn't be substituted directly
			return type;
		}

		if (type instanceof TypeVariable) {
			if (tv.equals(type)) {
				return replacement;
			}

			// If this is a type alias, preserve it unless we're explicitly substituting it
			TypeVariable typeVar = (TypeVariable) type;
			if (typeAliases.containsKey(typeVar.name.text) && !typeVar.equals(tv)) {
				return type; // Preserve the type alias
			}

			return type;
		}

		if (type instanceof FunctionType) {
			FunctionType ft = (FunctionType) type;

			List<Type> newArgs = new ArrayList<>();
			boolean argsChanged = false;

			for (Type argType : ft.args) {
				Type newArgType = substituteInType(argType, tv, replacement);
				newArgs.add(newArgType);
				if (newArgType != argType) {
					argsChanged = true;
				}
			}

			Type newReturnType = substituteInType(ft.type, tv, replacement);

			if (argsChanged || newReturnType != ft.type) {
				return new FunctionType(newArgs, newReturnType);
			}
			return ft;
		}
		if (type instanceof AppType) {
			AppType at = (AppType) type;

			List<Type> newArgs = new ArrayList<>();
			boolean argsChanged = false;

			for (Type argType : at.args) {
				Type newArgType = substituteInType(argType, tv, replacement);
				newArgs.add(newArgType);
				if (newArgType != argType) {
					argsChanged = true;
				}
			}

			if (argsChanged) {
				return new AppType(at.cons, newArgs);
			}
			return at;
		}
		if (type instanceof UnionType) {
			UnionType ut = (UnionType) type;

			List<Type> newTypes = new ArrayList<>();
			boolean typesChanged = false;

			for (Type unionType : ut.types) {
				Type newType = substituteInType(unionType, tv, replacement);
				newTypes.add(newType);
				if (newType != unionType) {
					typesChanged = true;
				}
			}

			if (typesChanged) {
				return new UnionType(newTypes);
			}
			return ut;
		}
		if (type instanceof ObjectType) {
			ObjectType ot = (ObjectType) type;
			HashMap<Token, Type> newFields = new HashMap<>();
			boolean fieldsChanged = false;

			for (Map.Entry<Token, Type> entry : ot.fields.entrySet()) {
				Type newFieldType = substituteInType(entry.getValue(), tv, replacement);
				newFields.put(entry.getKey(), newFieldType);
				if (newFieldType != entry.getValue()) {
					fieldsChanged = true;
				}
			}

			if (fieldsChanged) {
				return new ObjectType(newFields);
			}
			return ot;
		}
		return type;
	}

	// Also need to add support for simple variant constructors used directly
	private Type inferVariantReturn(Type type, List<Type> argTypes) {
		if (type instanceof TypeVariable t && ((TypeVariable) type).name.text.length() > 0
						&& Character.isUpperCase(((TypeVariable) type).name.text.charAt(0))) {

			return new AppType(t.name, argTypes);

			// Not a variant, return the original type
		}
		return type;
	}

	private CheckedNode inferIfElse(IfElse ifElse) {
		// 1. Check that the condition is a boolean
		CheckedNode condChecked = infer(ifElse.cond);
		Type condType = condChecked.type;

		try {
			unify(condType, new BooleanType(), ifElse.cond);
			// Apply substitutions after unification
			applyAllSubstitutions();
		} catch (TypeMismatchError e) {
			Errors.reportTypeCheckError(getTokenFromAst(ifElse.cond),
							"Condition of if-else must be a boolean, but got " + condType);
			System.exit(0);
		}

		// 2. Infer types for both branches
		CheckedNode thenChecked = infer(ifElse.then);
		CheckedNode elseChecked = infer(ifElse.elze);

		// Apply substitutions to get the most concrete types
		Type thenType = applySubstitutions(thenChecked.type);
		Type elseType = applySubstitutions(elseChecked.type);

		// 3. If both branches have the same type, return that type
		if (thenType.equals(elseType)) {
			return new CheckedNode(thenType, ifElse);
		}

		// 4. If types are different, create a union type
		List<Type> unionTypes = new ArrayList<>();

		// Add thenType to the union
		if (thenType instanceof UnionType) {
			// If thenType is already a union, add all its types
			unionTypes.addAll(((UnionType) thenType).types);
		} else {
			unionTypes.add(thenType);
		}

		// Add elseType to the union
		if (elseType instanceof UnionType) {
			// If elseType is already a union, add all its types
			unionTypes.addAll(((UnionType) elseType).types);
		} else {
			// Check if this type is already in the union to avoid duplicates
			boolean alreadyExists = false;
			for (Type existingType : unionTypes) {
				if (existingType.equals(elseType)) {
					alreadyExists = true;
					break;
				}
			}
			if (!alreadyExists) {
				unionTypes.add(elseType);
			}
		}

		// Create the final union type
		UnionType unionType = new UnionType(unionTypes);

		return new CheckedNode(unionType, ifElse);
	}

	// Enhanced method to handle type constraints for all types
	private void enforceStringType(Type type, Ast context) throws TypeMismatchError {
		Type applied = applySubstitutions(type);

		if (applied instanceof TypeVariable) {
			TypeVariable tv = (TypeVariable) applied;
			StringType stringType = new StringType();
			substitutions.put(tv, stringType);

			// Propagate to all related type variables
			for (Map.Entry<TypeVariable, Type> entry : substitutions.entrySet()) {
				if (entry.getValue() instanceof TypeVariable
								&& entry.getValue().equals(tv)) {
					substitutions.put(entry.getKey(), stringType);
				}
			}
		} else if (applied instanceof StringType) {
			// Already a string, nothing to do
		} else {
			throw new TypeMismatchError("Expected String but got " + applied);
		}
	}

	private CheckedNode inferRObject(RObject obj) {
		// Create a HashMap to store field types
		HashMap<Token, Type> fieldTypes = new HashMap<>();

		// For each field in the object
		for (Map.Entry<Token, Ast> entry : obj.obj.entrySet()) {
			Token fieldName = entry.getKey();
			Ast fieldValue = entry.getValue();

			// Special case for when field value is an identifier with same name as field
			if (fieldValue instanceof Identifier && ((Identifier) fieldValue).value.text.equals(fieldName.text)) {
				String varName = ((Identifier) fieldValue).value.text;

				// Check if this variable exists in our environment
				if (typeEnv.containsKey(varName)) {
					Type fieldType = typeEnv.get(varName);
					fieldTypes.put(fieldName, fieldType);
					continue;
				}
			}

			// Normal case: Infer the type of the field value
			CheckedNode fieldChecked = infer(fieldValue);
			Type fieldType = fieldChecked.type;

			// Apply substitutions to get the most concrete type
			fieldType = applySubstitutions(fieldType);

			// Add the field and its type to our map
			fieldTypes.put(fieldName, fieldType);
		}

		// Create an ObjectType with the inferred field types
		ObjectType objectType = new ObjectType(fieldTypes);

		return new CheckedNode(objectType, obj);
	}

	// New method to resolve type aliases
	private Type resolveTypeAlias(Type type) {
		if (type instanceof NamedType) {
			NamedType nt = (NamedType) type;
			if (typeAliases.containsKey(nt.name.text)) {
				return typeAliases.get(nt.name.text).type;
			}
		}
		return type;
	}

	// Completely revise the presentFinalType method to be more conservative
	private Type presentFinalType(Type type) {
		// Don't convert primitive types to aliases unless they're directly 
		// involved with a user-defined type variable

		// For function types, recurse on args and return type
		if (type instanceof FunctionType) {
			FunctionType ft = (FunctionType) type;
			List<Type> newArgs = new ArrayList<>();
			boolean changed = false;

			for (Type argType : ft.args) {
				Type presented = presentFinalType(argType);
				newArgs.add(presented);
				if (presented != argType) {
					changed = true;
				}
			}

			Type newReturnType = presentFinalType(ft.type);
			if (newReturnType != ft.type || changed) {
				return new FunctionType(newArgs, newReturnType);
			}
		}

		// Only preserve explicit user-defined type variables that correspond to aliases
		if (type instanceof TypeVariable) {
			TypeVariable tv = (TypeVariable) type;
			if (tv.is_user_defined && typeAliases.containsKey(tv.name.text)) {
				// This is a user-defined type that matches an alias name, preserve it
				return tv;
			}
		}

		// Don't convert primitive types to aliases automatically
		return type;
	}
}
