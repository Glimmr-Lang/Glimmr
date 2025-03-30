package roy.typechecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import roy.ast.*;
import roy.errors.Errors;
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
	private Map<TypeVariable, Type> substitutions;
	private Map<String, RFunction> functionTable; // Function table for all functions
	private Map<String, Boolean> checkedFunctions; // Track which functions have been checked
	private Map<String, Type> functionTypes; // Store function types separately

	public TypeChecker(List<Ast> nodes) {
		this.nodes = nodes;
		this.typeEnv = new HashMap<>();
		this.substitutions = new HashMap<>();
		this.functionTable = new HashMap<>();
		this.checkedFunctions = new HashMap<>();
		this.functionTypes = new HashMap<>();

		// First pass: register all functions in the function table
		registerFunctions();
	}

	// First pass: collect all function declarations
	private void registerFunctions() {
		for (Ast node : nodes) {
			if (node instanceof RFunction) {
				RFunction func = (RFunction) node;
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
			}
		}
	}

	public void process() {
		// Process all function declarations to set their types
		for (Map.Entry<String, RFunction> entry : functionTable.entrySet()) {
			// Create completely new maps for each function
			substitutions = new HashMap<>();
			typeEnv = new HashMap<>();

			// Double-check that they're completely empty
			substitutions.clear();
			typeEnv.clear();

			String functionName = entry.getKey();
			if (!checkedFunctions.get(functionName)) {
				inferFunction(entry.getValue());
			}
		}

		// Print results
		for (Ast node : nodes) {
			if (node instanceof RFunction) {
				RFunction func = (RFunction) node;
				String name = func.name.text;
				System.out.println("Function: " + name);
				System.out.println("Inferred type: " + functionTypes.get(name));
				System.out.println();
			}
		}
	}

	// Create a fresh type variable using a token
	private TypeVariable freshTypeVar(Token token) {
		return new TypeVariable(token);
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
		} else {
			// For other node types, report an error
			Token token = getTokenFromAst(node);
			Errors.reportTypeCheckError(token, "Type inference not implemented for: " + node.getClass().getName());
			System.exit(0);
			return null;
		}
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
		}

		// Default to the first token we can find
		if (ast instanceof IfElse) {
			return getTokenFromAst(((IfElse) ast).cond);
		}

		// Fallback in case we can't extract a token
		return new Token(TokenKind.ERR, "unknown");
	}

	private CheckedNode inferIdentifier(Identifier id) {
		String name = id.value.text;

		// Check if it's a function
		if (functionTable.containsKey(name)) {
			// If the function hasn't been type-checked yet, do it now
			if (!checkedFunctions.get(name)) {
				RFunction func = functionTable.get(name);
				inferFunction(func);
			}

			// Return the function type
			return new CheckedNode(functionTypes.get(name), id);
		}

		// Otherwise check if it's a local variable
		if (typeEnv.containsKey(name)) {
			return new CheckedNode(typeEnv.get(name), id);
		}

		// Check if it's a constructor (starts with uppercase letter)
		if (Character.isUpperCase(name.charAt(0))) {
			// This is a variant constructor
			return new CheckedNode(new TypeVariable(id.value, true), id);
		}

		// Not found in either table
		Errors.reportTypeCheckError(id.value, "Usage of an undefined variable `" + name + "`");
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
				// First check if we already have constraints that would create a conflict
				if (isPrimitiveType(leftType) && !(leftType instanceof StringType)) {
					throw new TypeMismatchError("Left operand of ++ must be a string, but got " + leftType);
				}

				if (isPrimitiveType(rightType) && !(rightType instanceof StringType)) {
					throw new TypeMismatchError("Right operand of ++ must be a string, but got " + rightType);
				}

				// Now enforce string constraints
				if (leftType instanceof TypeVariable) {
					substituteTypeVar((TypeVariable) leftType, stringType);
					unify(rightType, stringType, binOp.rhs);
				} else {
					unify(leftType, stringType, binOp.lhs);
				}

				if (rightType instanceof TypeVariable) {
					substituteTypeVar((TypeVariable) rightType, stringType);
					unify(rightType, stringType, binOp.rhs);
				} else {
					unify(rightType, stringType, binOp.rhs);
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

		// If we've already checked this function, return its type
		if (checkedFunctions.containsKey(funcName) && checkedFunctions.get(funcName)) {
			return new CheckedNode(functionTypes.get(funcName), func);
		}

		// Mark this function as being checked to handle recursive functions
		checkedFunctions.put(funcName, true);

		// Double-check that environment is clean
		if (!typeEnv.isEmpty()) {
			typeEnv.clear();  // Ensure environment is empty
		}
		if (!substitutions.isEmpty()) {
			substitutions.clear();  // Ensure substitutions are empty
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

		// Check the function body
		CheckedNode bodyChecked = infer(func.body);
		try {
			unify(func.type, bodyChecked.type, func.body);
		} catch (TypeMismatchError e) {
			Errors.reportTypeCheckError(getTokenFromAst(func.body), e.getMessage());
		}

		Type returnType = bodyChecked.type;

		// Detect string concatenation in the function body
		detectAndHandleOperations(func.body, argTypes);

		// Apply substitutions aggressively
		applyAllSubstitutions();

		// Apply substitutions to all types
		List<Type> substitutedArgTypes = new ArrayList<>();
		for (Type argType : argTypes) {
			Type substitutedType = applySubstitutions(argType);
			substitutedArgTypes.add(substitutedType);
		}
		Type substitutedReturnType = applySubstitutions(returnType);

		// After type checking the body, check if the body involves constructors
		if (substitutedReturnType instanceof TypeVariable
			&& ((TypeVariable) substitutedReturnType).name.text.length() > 0
			&& Character.isUpperCase(((TypeVariable) substitutedReturnType).name.text.charAt(0))) {
			// If the function directly returns a constructor
			substitutedReturnType = substitutedReturnType;
		} else if (func.body instanceof Call) {
			Call call = (Call) func.body;
			if (call.expr instanceof Identifier) {
				String constructorName = ((Identifier) call.expr).value.text;
				if (Character.isUpperCase(constructorName.charAt(0))) {
					// The function returns a constructor application
					Type constructorType = new TypeVariable(((Identifier) call.expr).value, true);
					List<Type> constructorArgs = new ArrayList<>();

					for (Ast arg : call.params) {
						if (arg instanceof Identifier) {
							String argName = ((Identifier) arg).value.text;
							for (int i = 0; i < func.args.size(); i++) {
								if (func.args.get(i).name.text.equals(argName)) {
									constructorArgs.add(substitutedArgTypes.get(i));
									break;
								}
							}
						} else {
							CheckedNode argChecked = infer(arg);
							constructorArgs.add(argChecked.type);
						}
					}

					// Create AppType for constructor application
					substitutedReturnType = inferVariantReturn(constructorType, constructorArgs);
				}
			}
		}

		// Create the final function type
		FunctionType finalFuncType = new FunctionType(substitutedArgTypes, substitutedReturnType);

		// Update function type in our registry
		functionTypes.put(funcName, finalFuncType);

		return new CheckedNode(finalFuncType, func);
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
				for (Identifier id : identifiers) {
					String name = id.value.text;
					if (typeEnv.containsKey(name)) {
						typeEnv.put(name, stringType);
					}
				}

				// Also unify operands directly
				if (binOp.lhs instanceof Identifier) {
					typeEnv.put(((Identifier) binOp.lhs).value.text, stringType);
				}

				if (binOp.rhs instanceof Identifier) {
					typeEnv.put(((Identifier) binOp.rhs).value.text, stringType);
				}

				// Directly constrain function arguments if they're used in this operation
				for (int i = 0; i < argTypes.size(); i++) {
					if (argTypes.get(i) instanceof TypeVariable) {
						for (Identifier id : identifiers) {
							if (typeEnv.containsKey(id.value.text)
								&& typeEnv.get(id.value.text).equals(argTypes.get(i))) {
								argTypes.set(i, stringType);
							}
						}
					}
				}
			} // Similarly for numeric operations
			else if (op == TokenKind.ADDITIVE_OPERATOR || op == TokenKind.MULTPLICATIVE_OPERATOR) {
				NumberType numberType = new NumberType();
				for (Identifier id : identifiers) {
					String name = id.value.text;
					if (typeEnv.containsKey(name)) {
						typeEnv.put(name, numberType);
					}
				}

				// Also unify operands directly
				if (binOp.lhs instanceof Identifier) {
					typeEnv.put(((Identifier) binOp.lhs).value.text, numberType);
				}

				if (binOp.rhs instanceof Identifier) {
					typeEnv.put(((Identifier) binOp.rhs).value.text, numberType);
				}

				// Directly constrain function arguments
				for (int i = 0; i < argTypes.size(); i++) {
					if (argTypes.get(i) instanceof TypeVariable) {
						for (Identifier id : identifiers) {
							if (typeEnv.containsKey(id.value.text)
								&& typeEnv.get(id.value.text).equals(argTypes.get(i))) {
								argTypes.set(i, numberType);
							}
						}
					}
				}
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
		}
		// Add other node types as needed
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
		// Similar to function inference but for lambdas
		Map<String, Type> oldEnv = new HashMap<>(typeEnv);
		Map<TypeVariable, Type> oldSubstitutions = new HashMap<>(substitutions);

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

		CheckedNode bodyChecked = infer(closure.body);
		Type returnType = bodyChecked.type;

		// Apply substitutions aggressively
		applyAllSubstitutions();

		// Apply substitutions
		List<Type> substitutedArgTypes = new ArrayList<>();
		for (Type argType : argTypes) {
			substitutedArgTypes.add(applySubstitutions(argType));
		}
		Type substitutedReturnType = applySubstitutions(returnType);

		typeEnv = oldEnv;
		substitutions = oldSubstitutions;

		// Create function type
		FunctionType functionType = new FunctionType(substitutedArgTypes, substitutedReturnType);

		return new CheckedNode(functionType, closure);
	}

	private CheckedNode inferCall(Call call) {
		// Check if this is a variant constructor application
		if (call.expr instanceof Identifier id
			&& !functionTable.containsKey(id.value.text)
			&& Character.isUpperCase(id.value.text.charAt(0))) {
			String name = id.value.text;
			// This is a variant constructor application
			List<Type> argTypes = new ArrayList<>();

			// Infer types for all arguments
			for (Ast arg : call.params) {
				CheckedNode argChecked = infer(arg);
				argTypes.add(argChecked.type);
			}

			// Create an AppType with the constructor name and argument types
			TypeVariable constructorType = new TypeVariable(id.value, true);

			// Simple case: Single argument constructor (e.g., Some a)
			return new CheckedNode(new AppType(constructorType, argTypes), call);
		} else if (call.expr instanceof Identifier id
			&& !functionTable.containsKey(id.value.text)) {
			Errors.reportTypeCheckError(id.value, "Call to undefined function `" + id.toString() + "`");
		}

		// Regular function call processing continues below...
		// Infer the type of the function being called
		CheckedNode funcChecked = infer(call.expr);
		Type funcType = funcChecked.type;

		// Check that the function type is a function
		if (!(funcType instanceof FunctionType)) {
			Errors.reportTypeCheckError(getTokenFromAst(call.expr),
				"Cannot call a non-function type: " + funcType);
			System.exit(0);
			return null;
		}

		FunctionType ft = (FunctionType) funcType;

		// Apply arguments one by one
		Type resultType = ft;

		// Check if number of arguments provided matches or is less than expected
		if (call.params.size() > ft.args.size()) {
			Errors.reportTypeCheckError(getTokenFromAst(call.expr),
				"Too many arguments provided to function call");
			System.exit(0);
			return null;
		}

		for (int i = 0; i < call.params.size(); i++) {
			// If we've processed all the arguments of current function type, error
			if (!(resultType instanceof FunctionType)) {
				Errors.reportTypeCheckError(getTokenFromAst(call.expr),
					"Too many arguments provided to function call");
				System.exit(0);
				return null;
			}

			ft = (FunctionType) resultType;
			Ast argAst = call.params.get(i);
			CheckedNode argChecked = infer(argAst);
			Type argType = argChecked.type;

			// Check that argument type matches parameter type
			try {
				unify(ft.args.get(0), argType, argAst);

				// Apply substitutions immediately after each argument
				applyAllSubstitutions();

				// If we've unified with a primitive type, ensure it propagates
				if (isPrimitiveType(argType)) {
					propagateTypeConstraint(argType);
				}

			} catch (TypeMismatchError e) {
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

		// Make sure to apply substitutions to the result
		resultType = applySubstitutions(resultType);

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

		if (t1 instanceof TypeVariable) {
			substituteTypeVar((TypeVariable) t1, t2);
			return;
		}

		if (t2 instanceof TypeVariable) {
			substituteTypeVar((TypeVariable) t2, t1);
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

		throw new TypeMismatchError(t1 + " is not compatible with " + t2);
	}

	// Substitute a type variable with a concrete type
	private void substituteTypeVar(TypeVariable tv, Type type) {
		if (type instanceof TypeVariable && tv.equals(type)) {
			return;
		}

		// Check for circular references
		if (occursIn(tv, type)) {
			return; // Instead of error, just return to avoid problematic substitutions
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
			}
		} while (changed);

		return result;
	}

	// Check if a type variable occurs in a type (occurs check)
	private boolean occursIn(TypeVariable tv, Type type) {
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
		return false;
	}

	// Substitute a type variable in a type with another type
	private Type substituteInType(Type type, TypeVariable tv, Type replacement) {
		if (type instanceof TypeVariable) {
			if (tv.equals(type)) {
				return replacement;
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
		return type;
	}

	// Also need to add support for simple variant constructors used directly
	private Type inferVariantReturn(Type type, List<Type> argTypes) {
		if (type instanceof TypeVariable && ((TypeVariable) type).name.text.length() > 0
			&& Character.isUpperCase(((TypeVariable) type).name.text.charAt(0))) {

			return new AppType(type, argTypes);

			// Not a variant, return the original type
		}
		return type;
	}
}
