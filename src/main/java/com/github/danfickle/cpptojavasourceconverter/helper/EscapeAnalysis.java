package com.github.danfickle.cpptojavasourceconverter.helper;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTASMDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTArraySubscriptExpression;
import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTBreakStatement;
import org.eclipse.cdt.core.dom.ast.IASTCaseStatement;
import org.eclipse.cdt.core.dom.ast.IASTCastExpression;
import org.eclipse.cdt.core.dom.ast.IASTCompoundStatement;
import org.eclipse.cdt.core.dom.ast.IASTConditionalExpression;
import org.eclipse.cdt.core.dom.ast.IASTContinueStatement;
import org.eclipse.cdt.core.dom.ast.IASTDeclarationStatement;
import org.eclipse.cdt.core.dom.ast.IASTDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTDefaultStatement;
import org.eclipse.cdt.core.dom.ast.IASTDoStatement;
import org.eclipse.cdt.core.dom.ast.IASTEqualsInitializer;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpressionList;
import org.eclipse.cdt.core.dom.ast.IASTExpressionStatement;
import org.eclipse.cdt.core.dom.ast.IASTFieldReference;
import org.eclipse.cdt.core.dom.ast.IASTForStatement;
import org.eclipse.cdt.core.dom.ast.IASTFunctionCallExpression;
import org.eclipse.cdt.core.dom.ast.IASTGotoStatement;
import org.eclipse.cdt.core.dom.ast.IASTIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTIfStatement;
import org.eclipse.cdt.core.dom.ast.IASTImplicitNameOwner;
import org.eclipse.cdt.core.dom.ast.IASTInitializer;
import org.eclipse.cdt.core.dom.ast.IASTInitializerClause;
import org.eclipse.cdt.core.dom.ast.IASTInitializerList;
import org.eclipse.cdt.core.dom.ast.IASTLabelStatement;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IASTNullStatement;
import org.eclipse.cdt.core.dom.ast.IASTProblemStatement;
import org.eclipse.cdt.core.dom.ast.IASTReturnStatement;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTSwitchStatement;
import org.eclipse.cdt.core.dom.ast.IASTTypeIdExpression;
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTWhileStatement;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.core.dom.ast.IParameter;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.ast.IVariable;
import org.eclipse.cdt.core.dom.ast.c.ICASTTypeIdInitializerExpression;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTCatchHandler;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTConstructorInitializer;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTDeleteExpression;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTForStatement;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNewExpression;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTSimpleTypeConstructorExpression;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTSwitchStatement;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTTryBlockStatement;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTUsingDeclaration;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTUsingDirective;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTWhileStatement;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPReferenceType;
import org.eclipse.cdt.core.dom.ast.gnu.IGNUASTCompoundStatementExpression;

import com.github.danfickle.cpptojavasourceconverter.MyLogger;
import com.github.danfickle.cpptojavasourceconverter.TypeManager;

public class EscapeAnalysis {
    private static class VariableState {
        String variable;
        boolean mayEscape = false;
    }
    
    private HashMap<IScope, HashMap<String, VariableState>> scopes = new HashMap<>();
    
    private EscapeAnalysis() {
    }

    public boolean mayVariableGetPointedToOrPassedByReference(IScope scope, String name) {
        HashMap<String, VariableState> variableStates = scopes.get(scope);
        if (variableStates == null) {
            return true;
        }
        VariableState variableState = variableStates.get(name);
        if (variableState == null) {
            return true;
        }
        return variableState.mayEscape;
    }

    public static EscapeAnalysis performEscapeAnalysis(IASTStatement statement) throws DOMException, ParsingException {
        EscapeAnalysis escapeAnalysis = new EscapeAnalysis();
        escapeAnalysis.escapeAnalyzeStatement(statement, false);
        return escapeAnalysis;
    }
    
    private void escapeAnalyzeStatement(IASTStatement statement, boolean isInsideAmpersand) throws DOMException, ParsingException {
        // The analysis must be conservative, ie. if we are unsure, it must assume the worst:
        // that the variable does escape. So must handle all cases - one unhandled statement
        // or expression means all variables may escape!!!
        if (statement instanceof IASTBreakStatement) {
        } else if (statement instanceof IASTCaseStatement) {
            IASTCaseStatement caseStatement = (IASTCaseStatement) statement;
            escapeAnalyzeExpression(caseStatement.getExpression(), isInsideAmpersand);
        } else if (statement instanceof IASTContinueStatement) {
        } else if (statement instanceof IASTDefaultStatement) {
        } else if (statement instanceof IASTGotoStatement){
        } else if (statement instanceof IASTNullStatement) {
        } else if (statement instanceof IASTProblemStatement) {
            IASTProblemStatement problemStatement = (IASTProblemStatement) statement;
            MyLogger.logImportant("problem: " + problemStatement.getProblem().getMessageWithLocation());
            throw new ParsingException(problemStatement.getProblem().getMessageWithLocation());
        } else if (statement instanceof IASTCompoundStatement) {
            IASTCompoundStatement compoundStatement = (IASTCompoundStatement)statement;
            for (IASTStatement childStatement : compoundStatement.getStatements()) {
                escapeAnalyzeStatement(childStatement, isInsideAmpersand);
            }
        } else if (statement instanceof IASTDeclarationStatement) {
            IASTDeclarationStatement declarationStatement = (IASTDeclarationStatement)statement;
            if (declarationStatement.getDeclaration() instanceof IASTSimpleDeclaration) {
                IASTSimpleDeclaration simpleDeclaration = (IASTSimpleDeclaration)declarationStatement.getDeclaration();
                for (IASTDeclarator declarator : simpleDeclaration.getDeclarators()) {
                    IBinding binding = declarator.getName().resolveBinding();
                    HashMap<String, VariableState> variableStates = scopes.get(binding.getScope());
                    if (variableStates == null) {
                        variableStates = new HashMap<String, VariableState>();
                        scopes.put(binding.getScope(), variableStates);
                    }
                    VariableState variableState = new VariableState();
                    variableState.variable = binding.getName();
                    variableStates.put(variableState.variable, variableState);
                    
                    if (declarator.getInitializer() != null) {
                        escapeAnalyzeInitializer(declarator.getInitializer(), isInsideAmpersand);
                    }
                    
                    if (binding instanceof IVariable) {
                        IVariable variable = (IVariable) binding;
                        if (TypeManager.expand(variable.getType()) instanceof ICPPReferenceType) {
                            escapeAnalyzeInitializer(declarator.getInitializer(), true);
                        }
                    }
                }
            } else if (declarationStatement.getDeclaration() instanceof IASTASMDeclaration) {
                throw new ParsingException("Use of assembly, cannot escape analyze statement");
            } else if (declarationStatement.getDeclaration() instanceof ICPPASTUsingDirective) {
            } else if (declarationStatement.getDeclaration() instanceof ICPPASTUsingDeclaration) {
            } else {
                throw new ParsingException("Unknown/unsupported declaration type, cannot escape analyze statement");
            }
        } else if (statement instanceof IASTDoStatement) {
            IASTDoStatement doStatement = (IASTDoStatement)statement;
            escapeAnalyzeStatement(doStatement.getBody(), isInsideAmpersand);
            escapeAnalyzeExpression(doStatement.getCondition(), isInsideAmpersand);
        } else if (statement instanceof IASTExpressionStatement) {
            IASTExpressionStatement expressionStatement = (IASTExpressionStatement)statement;
            escapeAnalyzeExpression(expressionStatement.getExpression(), isInsideAmpersand);
        } else if (statement instanceof IASTForStatement) {
            IASTForStatement forStatement = (IASTForStatement)statement;
            if (forStatement.getInitializerStatement() != null) {
                escapeAnalyzeStatement(forStatement.getInitializerStatement(), isInsideAmpersand);
            }
            if (forStatement.getConditionExpression() != null) {
                escapeAnalyzeExpression(forStatement.getConditionExpression(), isInsideAmpersand);
            }
            if (forStatement.getIterationExpression() != null) {
                escapeAnalyzeExpression(forStatement.getIterationExpression(), isInsideAmpersand);
            }
            if (forStatement instanceof ICPPASTForStatement && ((ICPPASTForStatement)forStatement).getConditionDeclaration() != null) {
                throw new ParsingException("Sorry C++'s declarations in a for statement are unsupported right now, escape analysis failed");
            }
            escapeAnalyzeStatement(forStatement.getBody(), isInsideAmpersand);
        } else if (statement instanceof IASTIfStatement) {
            IASTIfStatement ifStatement = (IASTIfStatement)statement;
            if (ifStatement.getConditionExpression() != null) {
                escapeAnalyzeExpression(ifStatement.getConditionExpression(), isInsideAmpersand);
            }
            escapeAnalyzeStatement(ifStatement.getThenClause(), isInsideAmpersand);
            if (ifStatement.getElseClause() != null) {
                escapeAnalyzeStatement(ifStatement.getElseClause(), isInsideAmpersand);
            }
        } else if (statement instanceof IASTLabelStatement) {
            IASTLabelStatement labelStatement = (IASTLabelStatement)statement;
            if (labelStatement.getNestedStatement() != null) {
                escapeAnalyzeStatement(labelStatement.getNestedStatement(), isInsideAmpersand);
            }
        } else if (statement instanceof IASTReturnStatement) {
            IASTReturnStatement returnStatement = (IASTReturnStatement)statement;
            escapeAnalyzeExpression(returnStatement.getReturnValue(), isInsideAmpersand);
        } else if (statement instanceof IASTSwitchStatement) {
            IASTSwitchStatement switchStatement = (IASTSwitchStatement)statement;
            escapeAnalyzeExpression(switchStatement.getControllerExpression(), isInsideAmpersand);
            if (switchStatement instanceof ICPPASTSwitchStatement &&
                    ((ICPPASTSwitchStatement) switchStatement).getControllerDeclaration() != null) {
                throw new ParsingException("Sorry C++'s switch declarations are unsupported right now, escape analysis failed");
            }
            escapeAnalyzeStatement(switchStatement.getBody(), isInsideAmpersand);
        } else if (statement instanceof IASTWhileStatement) {
            IASTWhileStatement whileStatement = (IASTWhileStatement)statement;
            escapeAnalyzeExpression(whileStatement.getCondition(), isInsideAmpersand);
            if (whileStatement instanceof ICPPASTWhileStatement &&
                    ((ICPPASTWhileStatement) whileStatement).getConditionDeclaration() != null) {
                throw new ParsingException("Sorry C++'s while declarations are unsupported right now, escape analysis failed");
            }
            escapeAnalyzeStatement(whileStatement.getBody(), isInsideAmpersand);
        } else if (statement instanceof ICPPASTTryBlockStatement) {
            ICPPASTTryBlockStatement tryBlockStatement = (ICPPASTTryBlockStatement) statement;
            escapeAnalyzeStatement(tryBlockStatement.getTryBody(), isInsideAmpersand);
            for (ICPPASTCatchHandler catchHandler : tryBlockStatement.getCatchHandlers()) {
                escapeAnalyzeStatement(catchHandler.getCatchBody(), isInsideAmpersand);
            }
        } else {
            StringBuilder interfaceList = new StringBuilder();
            String separator = "";
            for (Class<?> iface : statement.getClass().getInterfaces()) {
                interfaceList.append(separator);
                separator = ", ";
                interfaceList.append(iface);
            }
            throw new ParsingException("Unknown/unsupported IASTStatement: class " + statement.getClass().getName() + ", interfaces " + interfaceList);
        }
    }
    
    private void escapeAnalyzeExpression(IASTExpression expression, boolean isInsideAmpersand) throws DOMException, ParsingException {
        // Address taken: look for IASTUnaryExpression whose getOperator() == IASTUnaryExpression.op_amper and getOperand() contains a variable.
        // Passed by reference: 
        // Either way, we need to recursively search all subexpressions.
        if (expression instanceof IASTLiteralExpression) {
        } else if (expression instanceof IASTIdExpression) {
            IASTIdExpression idExpression = (IASTIdExpression) expression;
            if (isInsideAmpersand) {
                IBinding binding = idExpression.getName().resolveBinding();
                HashMap<String, VariableState> variables = scopes.get(binding.getScope());
                if (variables != null) {
                    VariableState variable = variables.get(binding.getName());
                    variable.mayEscape = true;
                }
            }
        } else if (expression instanceof IASTFieldReference) {
            IASTFieldReference fieldReferenceExpression = (IASTFieldReference) expression;
            escapeAnalyzeExpression(fieldReferenceExpression.getFieldOwner(), isInsideAmpersand);
        } else if (expression instanceof IASTUnaryExpression) {
            IASTUnaryExpression unaryExpression = (IASTUnaryExpression) expression;
            if (unaryExpression.getOperator() == IASTUnaryExpression.op_amper) {
                // we're taking the address of what?
                escapeAnalyzeExpression(unaryExpression.getOperand(), true);
            } else {
                escapeAnalyzeExpression(unaryExpression, isInsideAmpersand);
            }
        } else if (expression instanceof IASTConditionalExpression) {
            IASTConditionalExpression conditionalExpression = (IASTConditionalExpression) expression;
            escapeAnalyzeExpression(conditionalExpression.getLogicalConditionExpression(), isInsideAmpersand);
            escapeAnalyzeExpression(conditionalExpression.getPositiveResultExpression(), isInsideAmpersand);
            escapeAnalyzeExpression(conditionalExpression.getNegativeResultExpression(), isInsideAmpersand);
        } else if (expression instanceof IASTArraySubscriptExpression) {
            IASTArraySubscriptExpression arraySubscriptExpression = (IASTArraySubscriptExpression) expression;
            escapeAnalyzeExpression(arraySubscriptExpression.getArrayExpression(), isInsideAmpersand);
            if (arraySubscriptExpression.getArgument() instanceof IASTExpression) {
                escapeAnalyzeExpression((IASTExpression) arraySubscriptExpression.getArgument(), isInsideAmpersand);
            } else {
                throw new ParsingException("IArraySubscriptExpression argument not an IASTExpression");
            }
        } else if (expression instanceof IASTBinaryExpression) {
            IASTBinaryExpression binaryExpression = (IASTBinaryExpression) expression;
            escapeAnalyzeExpression(binaryExpression.getOperand1(), isInsideAmpersand);
            escapeAnalyzeExpression(binaryExpression.getOperand2(), isInsideAmpersand);
            if (binaryExpression.getInitOperand2() != null) {
                if (binaryExpression.getInitOperand2() instanceof IASTExpression) {
                    escapeAnalyzeExpression((IASTExpression)binaryExpression.getInitOperand2(), isInsideAmpersand);
                } else {
                    throw new ParsingException("IASTBinaryExpression initOperand2 not an IASTExpression");
                }
            }
        } else if (expression instanceof ICPPASTDeleteExpression) {
            ICPPASTDeleteExpression deleteExpression = (ICPPASTDeleteExpression) expression;
            escapeAnalyzeExpression(deleteExpression.getOperand(), isInsideAmpersand);
        } else if (expression instanceof ICPPASTNewExpression) {
            ICPPASTNewExpression newExpression = (ICPPASTNewExpression) expression;
            if (newExpression.getPlacementArguments() != null) {
                for (IASTInitializerClause initializerClause : newExpression.getPlacementArguments()) {
                    if (initializerClause instanceof IASTExpression) {
                        escapeAnalyzeExpression((IASTExpression) initializerClause, isInsideAmpersand);
                    } else {
                        throw new ParsingException("New expression's placement argument is not an IASTExpression");
                    }
                }
            }
            IASTInitializer initializer = newExpression.getInitializer();
            if (initializer != null) {
                escapeAnalyzeInitializer(initializer, isInsideAmpersand);
            }
        } else if (expression instanceof IASTFunctionCallExpression) {
            IASTFunctionCallExpression functionCallExpression = (IASTFunctionCallExpression) expression;
            escapeAnalyzeExpression(functionCallExpression.getFunctionNameExpression(), isInsideAmpersand);
            for (IASTInitializerClause initializerClause : functionCallExpression.getArguments()) {
                if (initializerClause instanceof IASTExpression) {
                    escapeAnalyzeExpression((IASTExpression) initializerClause, isInsideAmpersand);
                } else {
                    throw new ParsingException("Function call argument's initializer clause is not an IASTExpression");
                }
            }
            IBinding binding = null;
            if (functionCallExpression instanceof IASTImplicitNameOwner &&
                    ((IASTImplicitNameOwner)functionCallExpression).getImplicitNames() != null &&
                    ((IASTImplicitNameOwner)functionCallExpression).getImplicitNames().length > 0) {
                IASTImplicitNameOwner implicitNameOwner = (IASTImplicitNameOwner) functionCallExpression;
                if (implicitNameOwner.getImplicitNames().length == 1) {
                    binding = implicitNameOwner.getImplicitNames()[0].resolveBinding();
                } else {
                    throw new ParsingException("Function call casts to IASTImplicitNameOwner but has " +
                            implicitNameOwner.getImplicitNames().length + " implicit names");
                }
            } else {
                IASTExpression functionNameExpression = functionCallExpression.getFunctionNameExpression();
                if (functionNameExpression instanceof IASTIdExpression) {
                    binding = ((IASTIdExpression)functionNameExpression).getName().resolveBinding();
                } else if (functionNameExpression instanceof IASTFieldReference) {
                    binding = ((IASTFieldReference)functionNameExpression).getFieldName().resolveBinding();
                } else {
                    throw new ParsingException("Unknown/unsupported function name expression");
                }
            }
            IFunction function = (IFunction) binding;
            IParameter[] parameters = function.getParameters();
            IASTInitializerClause[] arguments = functionCallExpression.getArguments();
            for (int i = 0; i < arguments.length; i++) {
                if (TypeManager.expand(parameters[i].getType()) instanceof ICPPReferenceType) {
                    if (arguments[i] instanceof IASTExpression) {
                        escapeAnalyzeExpression((IASTExpression)arguments[i], true);
                    } else {
                        throw new ParsingException("Function call argument's initializer clause is not an IASTExpression");
                    }
                }
            }
        } else if (expression instanceof IASTCastExpression) {
            IASTCastExpression castExpression = (IASTCastExpression) expression;
            escapeAnalyzeExpression(castExpression.getOperand(), isInsideAmpersand);
        } else if (expression instanceof IASTTypeIdExpression) {
        } else if (expression instanceof ICASTTypeIdInitializerExpression) {
            ICASTTypeIdInitializerExpression typeIdInitializerExpression = (ICASTTypeIdInitializerExpression) expression;
            if (typeIdInitializerExpression.getInitializer() != null) {
                escapeAnalyzeInitializer(typeIdInitializerExpression.getInitializer(), isInsideAmpersand);
            }
        } else if (expression instanceof ICPPASTSimpleTypeConstructorExpression) {
            throw new ParsingException("ICPPASTSimpleTypeConstructorException unsupported");
        } else if (expression instanceof IGNUASTCompoundStatementExpression) {
            IGNUASTCompoundStatementExpression gnuCompoundStatementExpression = (IGNUASTCompoundStatementExpression)expression;
            escapeAnalyzeStatement(gnuCompoundStatementExpression.getCompoundStatement(), isInsideAmpersand);
        } else if (expression instanceof IASTExpressionList) {
            IASTExpressionList expressionList = (IASTExpressionList) expression;
            for (IASTExpression nestedExpression : expressionList.getExpressions()) {
                escapeAnalyzeExpression(nestedExpression, isInsideAmpersand);
            }
        } else {
            StringBuilder interfaceList = new StringBuilder();
            String separator = "";
            for (Class<?> iface : expression.getClass().getInterfaces()) {
                interfaceList.append(separator);
                separator = ", ";
                interfaceList.append(iface);
            }
            throw new ParsingException("Unknown/unsupported IASTExpression: class " + expression.getClass().getName() + ", interfaces " + interfaceList);
        }
    }
    
    private void escapeAnalyzeInitializer(IASTInitializer initializer, boolean isInsideAmpersand) throws DOMException, ParsingException {
        if (initializer instanceof IASTEqualsInitializer) {
            IASTEqualsInitializer equalsInitializer = (IASTEqualsInitializer) initializer;
            if (equalsInitializer.getInitializerClause() instanceof IASTExpression) {
                escapeAnalyzeExpression((IASTExpression) equalsInitializer.getInitializerClause(), isInsideAmpersand);
            } else {
                throw new ParsingException("New expression's equals initializer's initializer clause is not an IASTExpression");
            }
        } else if (initializer instanceof IASTInitializerList) {
            IASTInitializerList initializerList = (IASTInitializerList) initializer;
            for (IASTInitializerClause initializerClause : initializerList.getClauses()) {
                if (initializerClause instanceof IASTExpression) {
                    escapeAnalyzeExpression((IASTExpression) initializerClause, isInsideAmpersand);
                } else {
                    throw new ParsingException("New expression's initializer list's initializer clause is not an IASTExpression");
                }
            }
        } else if (initializer instanceof ICPPASTConstructorInitializer) {
            ICPPASTConstructorInitializer constructorInitializer = (ICPPASTConstructorInitializer) initializer;
            for (IASTInitializerClause initializerClause : constructorInitializer.getArguments()) {
                if (initializerClause instanceof IASTExpression) {
                    escapeAnalyzeExpression((IASTExpression) initializerClause, isInsideAmpersand);
                } else {
                    throw new ParsingException("New expression's constructor initializer's initializer clause is not an IASTExpression");
                }
            }
        } else {
            // FIXME: support the others
            throw new ParsingException("Unsupported initializer");
        }
    }
    
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<IScope, HashMap<String, VariableState>> entry : scopes.entrySet()) {
            out.append("Scope " + entry.getKey() + "\n");
            for (Map.Entry<String, VariableState> variableEntry : entry.getValue().entrySet()) {
                VariableState v = variableEntry.getValue();
                out.append("    " + v.variable + (v.mayEscape ? " may escape" : " doesn't escape") + "\n");
            }
        }
        return out.toString();
    }
}
