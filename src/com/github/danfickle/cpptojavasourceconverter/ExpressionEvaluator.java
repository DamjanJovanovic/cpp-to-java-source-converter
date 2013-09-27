package com.github.danfickle.cpptojavasourceconverter;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.*;
import org.eclipse.cdt.core.dom.ast.cpp.*;
import org.eclipse.cdt.core.dom.ast.gnu.*;

import com.github.danfickle.cpptojavasourceconverter.ExpressionModels.*;
import com.github.danfickle.cpptojavasourceconverter.TypeHelpers.TypeEnum;
import com.github.danfickle.cpptojavasourceconverter.TypeHelpers.TypeType;

class ExpressionEvaluator
{
	private GlobalContext ctx;
	
	ExpressionEvaluator(GlobalContext con)
	{
		ctx = con;
	}
	
	MExpression eval1Expr(IASTExpression expr) throws DOMException
	{
		List<MExpression> exprs = evalExpr(expr);
		assert(exprs.size() == 1);
		return exprs.get(0);
	}
	
	/**
	 * Given a C++ expression, attempts to convert it into one or more Java expressions.
	 */
	List<MExpression> evalExpr(IASTExpression expression) throws DOMException
	{
		List<MExpression> ret = new ArrayList<MExpression>();
		
		if (expression instanceof IASTLiteralExpression)
		{
			evalExprLiteral((IASTLiteralExpression) expression, ret);
		}
		else if (expression instanceof IASTIdExpression)
		{
			evalExprId((IASTIdExpression) expression, ret);
		}
		else if (expression instanceof IASTFieldReference)
		{
			evalExprFieldReference((IASTFieldReference) expression, ret);
		}
		else if (expression instanceof IASTUnaryExpression)
		{
			evalExprUnary((IASTUnaryExpression) expression, ret);
		}
		else if (expression instanceof IASTConditionalExpression)
		{
			evalExprConditional((IASTConditionalExpression) expression, ret);
		}
		else if (expression instanceof IASTArraySubscriptExpression)
		{
			evalExprArraySubscript((IASTArraySubscriptExpression) expression, ret);
		}
		else if (expression instanceof IASTBinaryExpression)
		{
			evalExprBinary((IASTBinaryExpression) expression, ret);
		}
		else if (expression instanceof ICPPASTDeleteExpression)
		{
			evalExprDelete((ICPPASTDeleteExpression) expression, ret);
		}
		else if (expression instanceof ICPPASTNewExpression)
		{
			evalExprNew((ICPPASTNewExpression) expression, ret);
		}
		else if (expression instanceof IASTFunctionCallExpression)
		{
			evalExprFuncCall((IASTFunctionCallExpression) expression, ret);
		}
		else if (expression instanceof IASTCastExpression)
		{
			evalCastExpression((IASTCastExpression) expression, ret);
		}
		else if (expression instanceof IASTTypeIdExpression)
		{
			//evalTypeIdExpression((IASTTypeIdExpression) expression, ret, flags);
		}
		else if (expression instanceof ICASTTypeIdInitializerExpression)
		{
			ICASTTypeIdInitializerExpression typeIdInitializerExpression = (ICASTTypeIdInitializerExpression)expression;

			ctx.converter.evalTypeId(typeIdInitializerExpression.getTypeId());
			//evaluate(typeIdInitializerExpression.getInitializer());
		}
		else if (expression instanceof ICPPASTSimpleTypeConstructorExpression)
		{
			ICPPASTSimpleTypeConstructorExpression simpleTypeConstructorExpression = (ICPPASTSimpleTypeConstructorExpression)expression;
			evalExpr(simpleTypeConstructorExpression.getInitialValue());
		}
		else if (expression instanceof IGNUASTCompoundStatementExpression)
		{
			IGNUASTCompoundStatementExpression compoundStatementExpression = (IGNUASTCompoundStatementExpression)expression;
			ctx.stmtEvaluator.evalStmt(compoundStatementExpression.getCompoundStatement());
		}
		else if (expression instanceof IASTExpressionList)
		{
			IASTExpressionList list = (IASTExpressionList) expression;

			for (IASTExpression childExpression : list.getExpressions())
				ret.addAll(evalExpr(childExpression));
		}
		else if (expression == null)
		{
			ret.add(new MEmptyExpression());
		}

		if (ret.isEmpty())
			MyLogger.logImportant(expression.getClass().getCanonicalName());

		if (expression != null)
			MyLogger.log(expression.getClass().getCanonicalName());

		return ret;
	}
	
	private void evalExprNew(ICPPASTNewExpression expr, List<MExpression> ret) throws DOMException
	{
		if (expr.isArrayAllocation() && !TypeHelpers.isObjectPtr(expr.getExpressionType()))
		{
			MNewArrayExpression ptr = new MNewArrayExpression();
			
			for (IASTExpression arraySize : expr.getNewTypeIdArrayExpressions())
				ptr.sizes.add(eval1Expr(arraySize));
			
			ptr.type = TypeHelpers.cppToJavaType(expr.getExpressionType());
			ret.add(ptr);
		}
		else if (expr.isArrayAllocation() && TypeHelpers.isObjectPtr(expr.getExpressionType()))
		{
			MNewArrayExpressionObject ptr = new MNewArrayExpressionObject();
			
			for (IASTExpression arraySize : expr.getNewTypeIdArrayExpressions())
				ptr.sizes.add(eval1Expr(arraySize));
			
			ptr.type = TypeHelpers.cppToJavaType(expr.getExpressionType());
			ret.add(ptr);
		}
		else if (!TypeHelpers.isObjectPtr(expr.getExpressionType()))
		{
			MNewExpression ptr = new MNewExpression();
			ptr.type = TypeHelpers.cppToJavaType(expr.getExpressionType());
			
			if (expr.getNewInitializer() != null)
				ptr.argument = eval1Expr(expr.getNewInitializer());
			else
			{
				MLiteralExpression lit = new MLiteralExpression();
				lit.literal = "0";
				ptr.argument = lit;
			}
			ret.add(ptr);
		}
		else
		{
			MNewExpressionObject ptr = new MNewExpressionObject();
			ptr.type = TypeHelpers.cppToJavaType(expr.getExpressionType());

			if (expr.getNewInitializer() instanceof IASTExpressionList)
			{
				for (IASTExpression arg : ((IASTExpressionList) expr.getNewInitializer()).getExpressions())
					ptr.arguments.addAll(evalExpr(arg));
			}
			else if (expr.getNewInitializer() instanceof IASTExpression)
			{
				ptr.arguments.addAll(evalExpr((IASTExpression) expr.getNewInitializer()));
			}
			
			ret.add(ptr);
		}
	}

	private void evalExprDelete(ICPPASTDeleteExpression expr, List<MExpression> ret) throws DOMException
	{
		if (TypeHelpers.isObjectPtr(expr.getOperand().getExpressionType()))
		{
			if (expr.isVectored())
			{
				MDeleteObjectMultiple del = new MDeleteObjectMultiple();
				del.operand = eval1Expr(expr.getOperand());
				ret.add(del);
			}
			else
			{
				MDeleteObjectSingle del = new MDeleteObjectSingle();
				del.operand = eval1Expr(expr.getOperand());
				ret.add(del);
			}
		}
		else
		{
			MEmptyExpression emp = new MEmptyExpression();
			ret.add(emp);
		}
	}
	
	private void evalExprId(IASTIdExpression expr, List<MExpression> ret) throws DOMException
	{
		if (ctx.bitfieldMngr.isBitfield(expr.getName()))
		{
			MIdentityExpressionBitfield ident = new MIdentityExpressionBitfield();
			ident.ident = TypeHelpers.getSimpleName(expr.getName());
			ret.add(ident);
		}
		else if (expr.getName().resolveBinding() instanceof IEnumerator)
		{
			MIdentityExpressionEnumerator ident = new MIdentityExpressionEnumerator();
			ident.enumName = ctx.enumMngr.getEnumerationName((IEnumerator) expr.getName().resolveBinding());
			ident.ident = TypeHelpers.getSimpleName(expr.getName());
			ret.add(ident);
		}
		else if (TypeHelpers.isEventualPtrBasic(expr.getExpressionType()))
		{
			MIdentityExpressionPtr ident = new MIdentityExpressionPtr();
			ident.ident = TypeHelpers.getSimpleName(expr.getName());
			ret.add(ident);
		}
		else if (ExpressionHelpers.isNumberExpression(expr))
		{
			MIdentityExpressionNumber ident = new MIdentityExpressionNumber();
			ident.ident = TypeHelpers.getSimpleName(expr.getName());
			ret.add(ident);
		}
		else
		{
			MIdentityExpressionPlain ident = new MIdentityExpressionPlain();
			ident.ident = TypeHelpers.getSimpleName(expr.getName());
			ret.add(ident);
		}
	}

	private void evalExprFieldReference(IASTFieldReference expr, List<MExpression> ret) throws DOMException
	{
		if (ctx.bitfieldMngr.isBitfield(expr.getFieldName()))
		{
			MFieldReferenceExpressionBitfield field = new MFieldReferenceExpressionBitfield();
			field.object = eval1Expr(expr.getFieldOwner());
			field.field = TypeHelpers.getSimpleName(expr.getFieldName());
			ret.add(field);
		}
		else if (expr.getFieldName().resolveBinding() instanceof IEnumerator)
		{
			MFieldReferenceExpressionEnumerator field = new MFieldReferenceExpressionEnumerator();
			field.object = eval1Expr(expr.getFieldOwner());
			field.field = TypeHelpers.getSimpleName(expr.getFieldName());
			ret.add(field);
		}
		else if (ExpressionHelpers.isNumberExpression(expr))
		{
			MFieldReferenceExpressionNumber field = new MFieldReferenceExpressionNumber();
			field.object = eval1Expr(expr.getFieldOwner());
			field.field = TypeHelpers.getSimpleName(expr.getFieldName());
			ret.add(field);
		}
		else if (TypeHelpers.isEventualPtrBasic(expr.getExpressionType()) && expr.isPointerDereference())
		{
			MFieldReferenceExpressionPtr field = new MFieldReferenceExpressionPtr();
			field.object = eval1Expr(expr.getFieldOwner());
			field.field = TypeHelpers.getSimpleName(expr.getFieldName());
			ret.add(field);
		}
		else
		{
			MFieldReferenceExpressionPlain field = new MFieldReferenceExpressionPlain();
			field.object = eval1Expr(expr.getFieldOwner());
			field.field = TypeHelpers.getSimpleName(expr.getFieldName());
			ret.add(field);
		}
	}


	private void evalExprConditional(IASTConditionalExpression expr, List<MExpression> ret) throws DOMException 
	{
		MTernaryExpression ternary = new MTernaryExpression();
		
		ternary.condition = eval1Expr(expr.getLogicalConditionExpression());
		ternary.condition = ExpressionHelpers.makeExpressionBoolean(ternary.condition, expr.getLogicalConditionExpression());
		ternary.positive = eval1Expr(expr.getPositiveResultExpression());
		ternary.negative = eval1Expr(expr.getNegativeResultExpression());
		
		ret.add(ternary);
	}

	private void evalCastExpression(IASTCastExpression expr, List<MExpression> ret) throws DOMException
	{
		MCastExpression cast = new MCastExpression();
		cast.operand = eval1Expr(expr.getOperand());
		// TODO cast.setType(evalTypeId(castExpression.getTypeId()));
	}

	private void evalExprArraySubscript(IASTArraySubscriptExpression expr, List<MExpression> ret) throws DOMException
	{
		//if (isEventualPtr(expr.getArrayExpression().getExpressionType()))
		{
			MArrayExpressionPtr ptr = new MArrayExpressionPtr();
			ptr.operand = eval1Expr(expr.getArrayExpression());
			ptr.subscript.addAll(evalExpr(expr.getSubscriptExpression()));
			ret.add(ptr);
		}
	}

	private void evalExprLiteral(IASTLiteralExpression lit, List<MExpression> ret) throws DOMException 
	{
		MLiteralExpression out = new MLiteralExpression();
		
		switch (lit.getKind())
		{
		case IASTLiteralExpression.lk_false:
			out.literal = "false";
			break;
		case IASTLiteralExpression.lk_true:
			out.literal = "true";
			break;
		case IASTLiteralExpression.lk_char_constant:
		case IASTLiteralExpression.lk_float_constant:
		case IASTLiteralExpression.lk_string_literal:
		case IASTLiteralExpression.lk_integer_constant:
			out.literal = String.valueOf(lit.getValue());
			break;
		case IASTLiteralExpression.lk_this:
			out.literal = "this";
			break;
		}
		
		ret.add(out);
	}
	
	private void modifyLiteralToPtr(MExpression lit) throws DOMException 
	{
		if (!(lit instanceof MLiteralExpression))
			return;
		
		MLiteralExpression expr = (MLiteralExpression) lit;
		
		if (expr.literal.equals("0"))
			expr.literal = "PtrObjNull.instance()";
		else if (expr.literal.equals("this"))
			/* Do nothing. */;
		else
		{
			MyLogger.logImportant("Not a ptr literal: " + expr.literal);
			MyLogger.exitOnError();
		}
	}
	
	private void evalExprUnary(IASTUnaryExpression expr, List<MExpression> ret) throws DOMException
	{
		if (expr.getOperator() == IASTUnaryExpression.op_bracketedPrimary)
		{
			MBracketExpression bra = new MBracketExpression();
			bra.operand = eval1Expr(expr.getOperand());
			ret.add(bra);
		}
		else if (expr.getOperator() == IASTUnaryExpression.op_amper)
		{
			if (ExpressionHelpers.isEventualPtrDeref(expr.getOperand()))
			{
				MAddressOfExpressionArrayItem add = new MAddressOfExpressionArrayItem();
				add.operand = eval1Expr(expr.getOperand());
				ret.add(add);
			}
			else if (TypeHelpers.getTypeEnum(expr.getOperand().getExpressionType()) == TypeEnum.BASIC_POINTER)
			{
				MAddressOfExpressionPtr add = new MAddressOfExpressionPtr();
				add.operand = eval1Expr(expr.getOperand());
				ret.add(add);
			}
			else
			{
				MAddressOfExpression add = new MAddressOfExpression();
				add.operand = eval1Expr(expr.getOperand());
				ret.add(add);
			}
		}
		else if (expr.getOperator() == IASTUnaryExpression.op_star)
		{
			MPrefixExpressionPointerStar pre = new MPrefixExpressionPointerStar();
			pre.operand = eval1Expr(expr.getOperand());
			ret.add(pre);
		}
		else if (TypeHelpers.isEventualPtrBasic(expr.getExpressionType()))
		{
			if (expr.getOperator() == IASTUnaryExpression.op_postFixIncr)
			{
				MPostfixExpressionPointerInc post = new MPostfixExpressionPointerInc();
				post.operand = eval1Expr(expr.getOperand());
				ret.add(post);
			}
			else if (expr.getOperator() == IASTUnaryExpression.op_postFixDecr)
			{
				MPostfixExpressionPointerDec post = new MPostfixExpressionPointerDec();
				post.operand = eval1Expr(expr.getOperand());
				ret.add(post);
			}
			else if (expr.getOperator() == IASTUnaryExpression.op_prefixDecr)
			{
				MPrefixExpressionPointerDec pre = new MPrefixExpressionPointerDec();
				pre.operand = eval1Expr(expr.getOperand());
				ret.add(pre);
			}
			else if (expr.getOperator() == IASTUnaryExpression.op_prefixIncr)
			{
				MPrefixExpressionPointerInc pre = new MPrefixExpressionPointerInc();
				pre.operand = eval1Expr(expr.getOperand());
				ret.add(pre);
			}
			else if (ExpressionHelpers.isPrefixExpression(expr.getOperator()))
			{
				MPrefixExpressionPointer pre = new MPrefixExpressionPointer();
				pre.operand = eval1Expr(expr.getOperand());
				pre.operator = ExpressionHelpers.evalUnaryPrefixOperator(expr.getOperator());
				ret.add(pre);
			}
			else if (expr.getOperator() == IASTUnaryExpression.op_amper)
			{
				MAddressOfExpression add = new MAddressOfExpression();
				add.operand = eval1Expr(expr.getOperand());
				ret.add(add);
			}
		}
		else if (ctx.bitfieldMngr.isBitfield(expr.getOperand()))
		{
			if (expr.getOperator() == IASTUnaryExpression.op_postFixIncr)
			{
				MPostfixExpressionBitfieldInc post = new MPostfixExpressionBitfieldInc();
				post.operand = eval1Expr(expr.getOperand());
				ret.add(post);
			}
			else if (expr.getOperator() == IASTUnaryExpression.op_postFixDecr)
			{
				MPostfixExpressionBitfieldDec post = new MPostfixExpressionBitfieldDec();
				post.operand = eval1Expr(expr.getOperand());
				ret.add(post);
			}
			if (expr.getOperator() == IASTUnaryExpression.op_prefixIncr)
			{
				MPrefixExpressionBitfieldInc post = new MPrefixExpressionBitfieldInc();
				post.operand = eval1Expr(expr.getOperand());
				ret.add(post);
			}
			else if (expr.getOperator() == IASTUnaryExpression.op_prefixDecr)
			{
				MPrefixExpressionBitfieldDec post = new MPrefixExpressionBitfieldDec();
				post.operand = eval1Expr(expr.getOperand());
				ret.add(post);
			}
			else if (ExpressionHelpers.isPrefixExpression(expr.getOperator()))
			{
				MPrefixExpressionBitfield pre = new MPrefixExpressionBitfield();
				pre.operand = eval1Expr(expr.getOperand());
				pre.operator = ExpressionHelpers.evalUnaryPrefixOperator(expr.getOperator());
				ret.add(pre);
			}
		}
		else if (ExpressionHelpers.isNumberExpression(expr.getOperand()))
		{
			if (expr.getOperator() == IASTUnaryExpression.op_postFixIncr)
			{
				MPostfixExpressionNumberInc post = new MPostfixExpressionNumberInc();
				post.operand = eval1Expr(expr.getOperand());
				ret.add(post);
			}
			else if (expr.getOperator() == IASTUnaryExpression.op_postFixDecr)
			{
				MPostfixExpressionNumberDec post = new MPostfixExpressionNumberDec();
				post.operand = eval1Expr(expr.getOperand());
				ret.add(post);
			}
			if (expr.getOperator() == IASTUnaryExpression.op_prefixIncr)
			{
				MPrefixExpressionNumberInc post = new MPrefixExpressionNumberInc();
				post.operand = eval1Expr(expr.getOperand());
				ret.add(post);
			}
			else if (expr.getOperator() == IASTUnaryExpression.op_prefixDecr)
			{
				MPrefixExpressionNumberDec post = new MPrefixExpressionNumberDec();
				post.operand = eval1Expr(expr.getOperand());
				ret.add(post);
			}
			else if (ExpressionHelpers.isPrefixExpression(expr.getOperator()))
			{
				MPrefixExpressionPlain pre = new MPrefixExpressionPlain();
				pre.operand = eval1Expr(expr.getOperand());
				pre.operator = ExpressionHelpers.evalUnaryPrefixOperator(expr.getOperator());
				ret.add(pre);
			}
		}
		// TODO else if (isEnumerator())
		else if (ExpressionHelpers.isPostfixExpression(expr.getOperator()))
		{
			MPostfixExpressionPlain postfix = new MPostfixExpressionPlain();
			postfix.operand = eval1Expr(expr.getOperand());
			postfix.operator = ExpressionHelpers.evalUnaryPostfixOperator(expr.getOperator());
			ret.add(postfix);
		}
		else if (ExpressionHelpers.isPrefixExpression(expr.getOperator()))
		{
			MPrefixExpressionPlain pre = new MPrefixExpressionPlain();
			pre.operand = eval1Expr(expr.getOperand());
			pre.operator = ExpressionHelpers.evalUnaryPrefixOperator(expr.getOperator());
			ret.add(pre);
		}
	}


	private void evalExprFuncCall(IASTFunctionCallExpression expr, List<MExpression> ret) throws DOMException
	{
		MFunctionCallExpressionParent func;
		
		if (expr.getFunctionNameExpression() instanceof IASTIdExpression &&
			((IASTIdExpression) expr.getFunctionNameExpression()).getName().resolveBinding() instanceof ICPPClassType)
		{
			func = new MClassInstanceCreation();
		}
		else
		{
			func = new MFunctionCallExpression();
		}

		func.name = eval1Expr(expr.getFunctionNameExpression());

		if (expr.getParameterExpression() instanceof IASTExpressionList)
		{
			IASTExpressionList list = (IASTExpressionList) expr.getParameterExpression();
			for (IASTExpression arg : list.getExpressions())
			{
				// TODO: Correct func arg type.
				MExpression exarg = wrapIfNeeded(arg, arg.getExpressionType());
				func.args.add(exarg);
			}
		}
		else if (expr.getParameterExpression() instanceof IASTExpression)
		{
			// TODO: Correct func arg type.
			MExpression exarg = wrapIfNeeded(expr.getParameterExpression(), expr.getParameterExpression().getExpressionType());
			func.args.add(exarg);
		}

		ret.add(func);
	}


	private void evalExprBinary(IASTBinaryExpression expr, List<MExpression> ret) throws DOMException 
	{
		if (ctx.bitfieldMngr.isBitfield(expr.getOperand1()))
		{
			if (expr.getOperator() == IASTBinaryExpression.op_assign)
			{
				MInfixAssignmentWithBitfieldOnLeft infix = new MInfixAssignmentWithBitfieldOnLeft();
				infix.left = eval1Expr(expr.getOperand1());
				infix.right = eval1Expr(expr.getOperand2());
				ret.add(infix);
			}
			else if (ExpressionHelpers.isAssignmentExpression(expr.getOperator()))
			{
				MCompoundWithBitfieldOnLeft infix = new MCompoundWithBitfieldOnLeft();
				infix.left = eval1Expr(expr.getOperand1());
				infix.right = eval1Expr(expr.getOperand2());
				infix.operator = ExpressionHelpers.compoundAssignmentToInfixOperator(expr.getOperator());
				ret.add(infix);
			}
			else
			{
				MInfixExpressionWithBitfieldOnLeft infix = new MInfixExpressionWithBitfieldOnLeft();
				infix.left = eval1Expr(expr.getOperand1());
				infix.right = eval1Expr(expr.getOperand2());
				infix.operator = ExpressionHelpers.evaluateBinaryOperator(expr.getOperator());
				ret.add(infix);
			}
		}
		else if (ExpressionHelpers.isNumberExpression(expr.getOperand1()))
		{
			MInfixExpression infix = null;
			
			if (expr.getOperator() == IASTBinaryExpression.op_assign)
			{
				infix = new MInfixAssignmentWithNumberOnLeft();
				infix.left = eval1Expr(expr.getOperand1());
				infix.right = eval1Expr(expr.getOperand2());
				ret.add(infix);
			}
			else if (ExpressionHelpers.isAssignmentExpression(expr.getOperator()))
			{
				infix = new MCompoundWithNumberOnLeft();
				infix.left = eval1Expr(expr.getOperand1());
				infix.right = eval1Expr(expr.getOperand2());
				infix.operator = ExpressionHelpers.compoundAssignmentToInfixOperator(expr.getOperator());
				ret.add(infix);
			}
			else
			{
				infix = new MInfixWithNumberOnLeft();
				infix.left = eval1Expr(expr.getOperand1());
				infix.right = eval1Expr(expr.getOperand2());
				infix.operator = ExpressionHelpers.evaluateBinaryOperator(expr.getOperator());
				ret.add(infix);
			}
			
			if (ExpressionHelpers.needBooleanExpressions(expr.getOperator()))
			{
				infix.left = ExpressionHelpers.makeExpressionBoolean(infix.left, expr.getOperand1());
				infix.right = ExpressionHelpers.makeExpressionBoolean(infix.right, expr.getOperand2());
			}
			else if (ExpressionHelpers.isBooleanExpression(expr.getOperand1()) && expr.getOperator() == IASTBinaryExpression.op_assign)
			{
				infix.right = ExpressionHelpers.makeExpressionBoolean(infix.right, expr.getOperand2());
			}
		}
		else if(ExpressionHelpers.isEventualPtrDeref(expr.getOperand1()))
		{
			if (expr.getOperator() == IASTBinaryExpression.op_assign)
			{
				MInfixAssignmentWithDerefOnLeft infix = new MInfixAssignmentWithDerefOnLeft();
				infix.left = eval1Expr(expr.getOperand1());
				infix.right = eval1Expr(expr.getOperand2());
				ret.add(infix);
			}
			else if (ExpressionHelpers.isAssignmentExpression(expr.getOperator()))
			{
				MCompoundWithDerefOnLeft infix = new MCompoundWithDerefOnLeft();
				infix.left = eval1Expr(expr.getOperand1());
				infix.right = eval1Expr(expr.getOperand2());
				infix.operator = ExpressionHelpers.compoundAssignmentToInfixOperator(expr.getOperator());
				ret.add(infix);
			}
			else
			{
				MInfixExpressionWithDerefOnLeft infix = new MInfixExpressionWithDerefOnLeft();
				infix.left = eval1Expr(expr.getOperand1());
				infix.right = eval1Expr(expr.getOperand2());
				infix.operator = ExpressionHelpers.evaluateBinaryOperator(expr.getOperator());
				ret.add(infix);
			}
		}
		else if (expr.getOperator() == IASTBinaryExpression.op_assign &&
				TypeHelpers.isEventualPtrBasic(expr.getOperand1().getExpressionType()))
		{
			MInfixAssignmentWithPtrOnLeft infix = new MInfixAssignmentWithPtrOnLeft();
			infix.left = eval1Expr(expr.getOperand1());
			infix.right = eval1Expr(expr.getOperand2());
			modifyLiteralToPtr(infix.right);
			
			ret.add(infix);
		}
		else if (ExpressionHelpers.isAssignmentExpression(expr.getOperator()) &&
				TypeHelpers.isEventualPtrBasic(expr.getOperand1().getExpressionType()))
		{
			MCompoundWithPtrOnLeft infix = new MCompoundWithPtrOnLeft();
			infix.left = eval1Expr(expr.getOperand1());
			infix.right = eval1Expr(expr.getOperand2());
			infix.operator = ExpressionHelpers.compoundAssignmentToInfixOperator(expr.getOperator());
			ret.add(infix);
		}
		else if ((expr.getOperator() == IASTBinaryExpression.op_minus ||
				 expr.getOperator() == IASTBinaryExpression.op_plus) &&
				 TypeHelpers.isEventualPtrBasic(expr.getOperand1().getExpressionType()))
		{
			MInfixExpressionWithPtrOnLeft infix = new MInfixExpressionWithPtrOnLeft();
			infix.left = eval1Expr(expr.getOperand1());
			infix.right = eval1Expr(expr.getOperand2());
			infix.operator = ExpressionHelpers.evaluateBinaryOperator(expr.getOperator());
			ret.add(infix);
		}
		else if ((expr.getOperator() == IASTBinaryExpression.op_minus ||
				 expr.getOperator() == IASTBinaryExpression.op_plus) &&
				 TypeHelpers.isEventualPtrBasic(expr.getOperand2().getExpressionType()))
		{
			MInfixExpressionWithPtrOnRight infix = new MInfixExpressionWithPtrOnRight();
			infix.left = eval1Expr(expr.getOperand1());
			infix.right = eval1Expr(expr.getOperand2());
			infix.operator = ExpressionHelpers.evaluateBinaryOperator(expr.getOperator());
			ret.add(infix);
		}
		else if (TypeHelpers.isEventualPtrBasic(expr.getOperand1().getExpressionType()))
		{
			MInfixExpressionPtrComparison infix = new MInfixExpressionPtrComparison();
			infix.left = eval1Expr(expr.getOperand1());
			infix.right = eval1Expr(expr.getOperand2());
			infix.operator = ExpressionHelpers.evaluateBinaryOperator(expr.getOperator());
			
			modifyLiteralToPtr(infix.left);
			modifyLiteralToPtr(infix.right);
			
			ret.add(infix);
		}
		else
		{
			MInfixExpressionPlain infix = new MInfixExpressionPlain();
			infix.left = eval1Expr(expr.getOperand1());
			infix.right = eval1Expr(expr.getOperand2());
			infix.operator = ExpressionHelpers.evaluateBinaryOperator(expr.getOperator());
			ret.add(infix);
		}
	}
	
	/**
	 * Gets the expressions for the array sizes.
	 * Eg. int a[1][2 + 5] returns a list containing expressions
	 * [1, 2 + 5].
	 */
	List<MExpression> getArraySizeExpressions(IType type) throws DOMException
	{
		List<MExpression> ret = new ArrayList<MExpression>();

		IArrayType arr = (IArrayType) type;
		ret.add(eval1Expr(arr.getArraySizeExpression()));

		while (arr.getType() instanceof IArrayType)
		{
			IArrayType arr2 = (IArrayType) arr.getType();
			ret.add(eval1Expr(arr2.getArraySizeExpression()));
			arr = arr2;
		}

		return ret;
	}
	
	/**
	 * This function should be called for return values, function arguments and rhs of direct
	 * assignment. It will wrap the expression, if required, so it is copied. 
	 */
	MExpression wrapIfNeeded(IASTExpression cppExpr, IType tpRequired) throws DOMException
	{
		if (TypeHelpers.getTypeEnum(tpRequired) == TypeEnum.BASIC_REFERENCE)
		{
			MRefWrapper wrap = new MRefWrapper();
			wrap.operand = eval1Expr(cppExpr);
			return wrap;
		}
		else if (TypeHelpers.getTypeEnum(tpRequired) == TypeEnum.BOOLEAN ||
				TypeHelpers.getTypeEnum(tpRequired) == TypeEnum.CHAR ||
				TypeHelpers.getTypeEnum(tpRequired) == TypeEnum.NUMBER)
		{
			 MValueOfExpressionNumber valOfExpr = new MValueOfExpressionNumber();
			 valOfExpr.type = TypeHelpers.cppToJavaType(tpRequired, TypeType.IMPLEMENTATION);
			 valOfExpr.operand = eval1Expr(cppExpr);
			 return valOfExpr;
		}
		else
		{
			return eval1Expr(cppExpr);
		}
	}
}
