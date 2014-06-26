package il.ac.technion.cs.ssdl.spartan.refactoring;

import static il.ac.technion.cs.ssdl.spartan.utils.Funcs.*;
import il.ac.technion.cs.ssdl.spartan.utils.Occurrences;
import il.ac.technion.cs.ssdl.spartan.utils.Range;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * @author Artium Nihamkin (original)
 * @author Boris van Sosin <code><boris.van.sosin [at] gmail.com></code> (v2)
 * @author Tomer Zeltzer <code><tomerr90 [at] gmail.com></code> (v3)
 * 
 * @since 2013/01/01
 */
public class Ternarize extends Spartanization {
	/** Instantiates this class */
	public Ternarize() {
		super("Ternarize", "Convert conditional to an expression using the ternary (?:) operator"
				+ "or to a return condition statement");
	}

	@Override protected final void fillRewrite(final ASTRewrite r, final AST t, final CompilationUnit cu,
			final IMarker m) {
		cu.accept(new ASTVisitor() {
			@Override public boolean visit(final IfStatement i) {
				return // try lot's of options, but finally return true.
				!inRange(m, i) // Stop here
						|| perhapsAssignIfAssign(t, r, i) //
						|| perhapsIfReturn(t, r, i) //
						|| perhapsIfSameExpStmntOrRet(t, r, i) //
						|| true // "i" is beyond hope, perhaps its children
				;
			}
		});
	}

	static boolean perhapsIfReturn(final AST ast, final ASTRewrite r, final IfStatement ifStmnt) {
		return null != asBlock(ifStmnt.getParent()) && treatIfReturn(ast, r, ifStmnt, asBlock(ifStmnt.getParent()));
	}

	private static boolean treatIfReturn(final AST ast, final ASTRewrite r, final IfStatement ifStmnt,
			final Block parent) {
		if (!checkIfReturnStmntExist(ifStmnt.getThenStatement()))
			return false;
		final ReturnStatement nextRet = nextStatement(statements(parent), statements(parent).indexOf(ifStmnt));
		return nextRet != null && 1 == statementsCount(ifStmnt.getThenStatement())
				&& 0 == statementsCount(ifStmnt.getElseStatement()) && rewriteIfToRetStmnt(ast, r, ifStmnt, nextRet);
	}

	private static ReturnStatement nextStatement(final List<ASTNode> ns, final int n) {
		return ns.size() <= n + 1 ? null : asReturn(ns.get(n + 1));
	}

	private static boolean rewriteIfToRetStmnt(final AST ast, final ASTRewrite r, final IfStatement ifStmnt,
			final ReturnStatement nextReturn) {
		final ReturnStatement thenRet = asReturn(ifStmnt.getThenStatement());
		return thenRet == null || isConditional(thenRet.getExpression(), nextReturn.getExpression()) ? false
				: rewriteIfToRetStmnt(ast, r, ifStmnt, thenRet.getExpression(), nextReturn.getExpression());
	}

	private static boolean rewriteIfToRetStmnt(final AST ast, final ASTRewrite r, final IfStatement ifStmnt,
			final Expression thenExp, final Expression nextExp) {
		r.replace(ifStmnt,
				makeReturnStatement(ast, r, determineNewExp(ast, r, ifStmnt.getExpression(), thenExp, nextExp)), null);
		r.remove(nextExp.getParent(), null);
		return true;
	}

	/**
	 * @author Tomer Zeltzer
	 * 
	 *         contains both sides for the conditional expression
	 */
	public static class TwoExpressions {
		final Expression thenExp;
		final Expression elseExp;

		/**
		 * Instantiates the class with the given Expressions
		 * 
		 * @param t
		 *            then Expression
		 * @param e
		 *            else Expression
		 */
		public TwoExpressions(final Expression t, final Expression e) {
			thenExp = t;
			elseExp = e;
		}
	}

	/**
	 * @author Tomer Zeltzer
	 * 
	 *         contains 2 nodes (used to store the 2 nodes that are different in
	 *         the then and else tree)
	 */
	public static class TwoNodes {
		ASTNode thenNode;
		ASTNode elseNode;

		/**
		 * Instantiates the class with the given nodes
		 * 
		 * @param t
		 *            then node
		 * @param e
		 *            else node
		 */
		public TwoNodes(final ASTNode t, final ASTNode e) {
			thenNode = t;
			elseNode = e;
		}
	}

	static boolean perhapsIfSameExpStmntOrRet(final AST ast, final ASTRewrite r, final IfStatement ifStmt) {
		final Statement thenStmt = getBlockSingleStmnt(ifStmt.getThenStatement());
		final Statement elseStmt = getBlockSingleStmnt(ifStmt.getElseStatement());
		return !hasNull(asBlock(ifStmt.getParent()), thenStmt, elseStmt)
				&& treatIfSameExpStmntOrRet(ast, r, ifStmt, thenStmt, elseStmt);
	}

	private static boolean treatIfSameExpStmntOrRet(final AST ast, final ASTRewrite r, final IfStatement ifStmt,
			final Statement thenStmnt, final Statement elseStmnt) {
		final List<TwoNodes> diffList = findDiffList(thenStmnt, elseStmnt);
		if (!isDiffListValid(diffList))
			return false;
		final int ifIdx = statements(ifStmt.getParent()).indexOf(ifStmt);
		final Statement possiblePrevDecl = (Statement) statements(ifStmt.getParent()).get(
				0 > ifIdx - 1 ? ifIdx : ifIdx - 1);
		boolean wasPrevDeclReplaced = false;
		for (int i = 0; i < diffList.size(); i++) {
			final TwoExpressions diffExps = findSingleDifference(diffList.get(i).thenNode, diffList.get(i).elseNode);
			if (isConditional(diffExps.thenExp, diffExps.elseExp))
				return false;
			if (canReplacePrevDecl(possiblePrevDecl, diffList.get(i)))
				wasPrevDeclReplaced = true;
			if (!isExpOnlyDiff(diffList.get(i), diffExps) || !substitute(ast, r, ifStmt, diffExps, possiblePrevDecl))
				return false;
		}
		if (!wasPrevDeclReplaced)
			r.replace(ifStmt, r.createCopyTarget(thenStmnt), null);
		return true;
	}

	private static boolean isDiffListValid(final List<TwoNodes> diffList) {
		if (diffList == null)
			return false;
		for (int i = 0; i < diffList.size(); i++) {
			if (!handleCaseDiffNodesAreBlocks(diffList.get(i)))
				return false;
			if (!isExpStmntOrRet(diffList.get(i).thenNode) || !isExpStmntOrRet(diffList.get(i).elseNode))
				if (isExpStmntOrRet(diffList.get(i).thenNode.getParent())
						&& isExpStmntOrRet(diffList.get(i).elseNode.getParent())) {
					diffList.get(i).thenNode = diffList.get(i).thenNode.getParent();
					diffList.get(i).elseNode = diffList.get(i).elseNode.getParent();
				} else
					return false;
			if (!areExpsValid(diffList.get(i)))
				return false;
		}
		return true;
	}

	private static boolean areExpsValid(final TwoNodes diffNodes) {
		return diffNodes.thenNode.getNodeType() != diffNodes.elseNode.getNodeType() ? false : areExpsValid(diffNodes,
				findDiffExps(diffNodes));
	}

	private static boolean areExpsValid(final TwoNodes diffNodes, final TwoExpressions diffExps) {
		return diffExps != null && !isConditional(diffExps.thenExp, diffExps.elseExp)
				&& !containIncOrDecExp(diffExps.thenExp, diffExps.elseExp) && isExpOnlyDiff(diffNodes, diffExps);

	}

	private static TwoExpressions findSingleDifference(final ASTNode thenStmnt, final ASTNode elseStmnt) {
		final TwoNodes diffNodes = new TwoNodes(thenStmnt, elseStmnt);
		if (!handleCaseDiffNodesAreBlocks(diffNodes))
			return null;
		final TwoExpressions $ = findDiffExps(diffNodes);
		if (isExpStmt(diffNodes.thenNode))
			return $;
		return $ == null || !isReturn(diffNodes.thenNode) ? null : new TwoExpressions(
				getExpression(diffNodes.thenNode), getExpression(diffNodes.elseNode));
	}

	private static TwoExpressions findDiffExps(final TwoNodes diffNodes) {
		TwoNodes tempNodes = findDiffNodes(diffNodes.thenNode, diffNodes.elseNode);
		if (isExpStmt(diffNodes.thenNode))
			tempNodes = findDiffNodes(tempNodes.thenNode, tempNodes.elseNode);
		return tempNodes == null ? null : new TwoExpressions((Expression) tempNodes.thenNode,
				(Expression) tempNodes.elseNode);
	}

	private static boolean isExpOnlyDiff(final TwoNodes diffNodes, final TwoExpressions diffExps) {
		if (diffExps == null)
			return !isAssignment(diffNodes.thenNode)
					|| !isAssignment(diffNodes.elseNode)
					|| compatible(getAssignment((ExpressionStatement) diffNodes.thenNode),
							getAssignment((ExpressionStatement) diffNodes.elseNode));
		if (hasNull(diffNodes.thenNode, diffNodes.elseNode))
			return false;
		if (isAssignment(diffNodes.thenNode) && isAssignment(diffNodes.elseNode))
			return compatible(getAssignment((ExpressionStatement) diffNodes.thenNode),
					getAssignment((ExpressionStatement) diffNodes.elseNode));
		final List<ASTNode> thenSubTree = prepareSubTree(diffNodes.thenNode, diffExps.thenExp);
		final List<ASTNode> elseSubTree = prepareSubTree(diffNodes.elseNode, diffExps.elseExp);
		return thenSubTree.toString().equals(elseSubTree.toString());
	}

	private static List<ASTNode> prepareSubTree(final ASTNode node, final Expression exp) {
		final List<ASTNode> $ = getChildren(node);
		if (isExpStmt(node))
			$.remove(((ExpressionStatement) node).getExpression());
		$.remove(exp);
		$.removeAll(getChildren(exp));
		return $;
	}

	private static boolean isExpStmntOrRet(final ASTNode n) {
		return n != null && (isExpStmt(n) || isReturn(n));
	}

	private static boolean handleCaseDiffNodesAreBlocks(final TwoNodes diffNodes) {
		if (1 != statementsCount(diffNodes.thenNode) || 1 != statementsCount(diffNodes.elseNode))
			return false;
		diffNodes.thenNode = getStmntIfBlock(diffNodes.thenNode);
		diffNodes.elseNode = getStmntIfBlock(diffNodes.elseNode);
		return true;
	}

	private static ASTNode getStmntIfBlock(final ASTNode n) {
		return n != null && n.getNodeType() == ASTNode.BLOCK ? getBlockSingleStmnt((Block) n) : n;
	}

	private static TwoNodes findDiffNodes(final ASTNode thenNode, final ASTNode elseNode) {
		return hasNull(thenNode, elseNode) ? null : findDiffNodes(getChildren(thenNode), getChildren(elseNode));
	}

	private static TwoNodes findDiffNodes(final List<ASTNode> thenList, final List<ASTNode> elseList) {
		for (int idx = 0; idx < thenList.size() && idx < elseList.size(); idx++)
			if (!thenList.get(idx).toString().equals(elseList.get(idx).toString()))
				return new TwoNodes(thenList.get(idx), elseList.get(idx));
		return null;
	}

	private static List<TwoNodes> findDiffList(final ASTNode thenNode, final ASTNode elseNode) {
		return hasNull(thenNode, elseNode) ? null : findDiffList(getChildren(thenNode), getChildren(elseNode));
	}

	private static List<TwoNodes> findDiffList(final List<ASTNode> thenList, final List<ASTNode> elseList) {
		final List<TwoNodes> $ = new ArrayList<>();
		for (int idx = 0; idx < thenList.size() && idx < elseList.size(); idx++)
			if (!thenList.get(idx).toString().equals(elseList.get(idx).toString())) {
				$.add(new TwoNodes(thenList.get(idx), elseList.get(idx)));
				thenList.removeAll(getChildren(thenList.get(idx)));
				elseList.removeAll(getChildren(elseList.get(idx)));
			}
		return $;
	}

	private static boolean substitute(final AST t, final ASTRewrite r, final IfStatement ifStmnt,
			final TwoExpressions diff, final Statement possiblePrevDecl) {
		final Statement elseStmnt = getBlockSingleStmnt(ifStmnt.getElseStatement());
		final Statement thenStmnt = getBlockSingleStmnt(ifStmnt.getThenStatement());
		final TwoNodes diffNodes = !isExpStmntOrRet(thenStmnt) ? findDiffNodes(thenStmnt, elseStmnt) : new TwoNodes(
				thenStmnt, elseStmnt);
		final Expression newExp = determineNewExp(t, r, ifStmnt.getExpression(), diff.thenExp, diff.elseExp);
		if (isAssignment(diffNodes.thenNode) && isAssignment(diffNodes.elseNode))
			if (!compatible(getAssignment((Statement) diffNodes.thenNode),
					getAssignment((Statement) diffNodes.elseNode)))
				return false;
			else if (canReplacePrevDecl(possiblePrevDecl, diffNodes))
				return handleSubIfDiffAreAsgns(t, r, ifStmnt, possiblePrevDecl, diffNodes.thenNode, newExp);
		r.replace(diff.thenExp, newExp, null);
		return true;
	}

	private static boolean canReplacePrevDecl(final Statement possiblePrevDecl, final TwoNodes diffNodes) {
		return !isExpStmt(diffNodes.thenNode) || diffNodes.thenNode.getNodeType() != diffNodes.elseNode.getNodeType() ? false
				: canReplacePrevDecl(possiblePrevDecl, (ExpressionStatement) diffNodes.thenNode,
						(ExpressionStatement) diffNodes.elseNode);
	}

	private static boolean canReplacePrevDecl(final Statement possiblePrevDecl, final ExpressionStatement thenExpStmt,
			final ExpressionStatement elseExpStmt) {
		final List<VariableDeclarationFragment> frags = !isVarDeclStmt(possiblePrevDecl) ? null
				: ((VariableDeclarationStatement) possiblePrevDecl).fragments();
		final Assignment asgnThen = getAssignment(thenExpStmt);
		final Assignment asgnElse = getAssignment(elseExpStmt);
		return hasNull(asgnThen, asgnElse, frags) || !isOpAssign(asgnThen)
				|| !compatibleOps(asgnThen.getOperator(), asgnElse.getOperator()) ? false : possibleToReplace(asgnThen,
				frags) && possibleToReplace(asgnElse, frags);
	}

	private static boolean handleSubIfDiffAreAsgns(final AST t, final ASTRewrite r, final IfStatement ifStmnt,
			final Statement possiblePrevDecl, final ASTNode thenNode, final Expression newExp) {
		final VariableDeclarationFragment prevDecl = getVarDeclFrag(possiblePrevDecl,
				getAssignment((Statement) thenNode).getLeftHandSide());
		r.replace(prevDecl, makeVarDeclFrag(t, r, prevDecl.getName(), newExp), null);
		r.remove(ifStmnt, null);
		return true;
	}

	private static Expression determineNewExp(final AST t, final ASTRewrite r, final Expression cond,
			final Expression thenExp, final Expression elseExp) {
		return isBoolLitrl(thenExp) && isBoolLitrl(elseExp) ? tryToNegateCond(t, r, cond,
				((BooleanLiteral) thenExp).booleanValue()) : makeParenthesizedConditionalExp(t, r, cond, thenExp,
				elseExp);
	}

	static boolean perhapsAssignIfAssign(final AST ast, final ASTRewrite r, final IfStatement ifStmnt) {
		return null != asBlock(ifStmnt.getParent())
				&& treatAssignIfAssign(ast, r, ifStmnt, statements(asBlock(ifStmnt.getParent())));
	}

	private static boolean treatAssignIfAssign(final AST ast, final ASTRewrite r, final IfStatement ifStmnt,
			final List<ASTNode> stmts) {
		final int ifIdx = stmts.indexOf(ifStmnt);
		final Assignment asgnThen = getAssignment(ifStmnt.getThenStatement());
		if (asgnThen == null || null != ifStmnt.getElseStatement() || ifIdx < 1)
			return false;
		final Assignment prevAsgn = getAssignment((Statement) stmts.get(ifIdx - 1));
		final Assignment nextAsgn = stmts.size() <= ifIdx + 1 ? null : getAssignment((Statement) stmts.get(ifIdx + 1));
		final VariableDeclarationFragment prevDecl = findPrevDecl(stmts, ifIdx, asgnThen, prevAsgn, nextAsgn);
		return tryHandleNextAndPrevAsgnExist(r, ifStmnt, asgnThen, prevAsgn, nextAsgn, prevDecl) //
				|| tryHandleOnlyPrevAsgnExist(ast, r, ifStmnt, asgnThen, prevAsgn, prevDecl) //
				|| tryHandleOnlyNextAsgnExist(ast, r, ifStmnt, asgnThen, nextAsgn, prevDecl) //
				|| tryHandleNoNextNoPrevAsgn(ast, r, ifStmnt, asgnThen, prevAsgn, nextAsgn, prevDecl);
	}

	private static boolean tryHandleNoNextNoPrevAsgn(final AST ast, final ASTRewrite r, final IfStatement ifStmnt,
			final Assignment asgnThen, final Assignment prevAsgn, final Assignment nextAsgn,
			final VariableDeclarationFragment prevDecl) {
		if (!isNoNextNoPrevAsgnPossible(ifStmnt, asgnThen, prevAsgn, nextAsgn, prevDecl))
			return false;
		r.replace(
				prevDecl,
				makeVarDeclFrag(
						ast,
						r,
						prevDecl.getName(),
						makeParenthesizedConditionalExp(ast, r, ifStmnt.getExpression(), asgnThen.getRightHandSide(),
								prevDecl.getInitializer())), null);
		r.remove(ifStmnt, null);
		return true;
	}

	private static boolean isNoNextNoPrevAsgnPossible(final IfStatement ifStmnt, final Assignment asgnThen,
			final Assignment prevAsgn, final Assignment nextAsgn, final VariableDeclarationFragment prevDecl) {
		return prevAsgn == null //
				&& nextAsgn == null //
				&& !isConditional(asgnThen.getRightHandSide()) //
				&& prevDecl != null //
				&& null != prevDecl.getInitializer() //
				&& null == ifStmnt.getElseStatement() //
				&& !isConditional(prevDecl.getInitializer()) //
				&& !dependsOn(prevDecl.getName(), ifStmnt.getExpression(), asgnThen.getRightHandSide())//
		;
	}

	private static boolean tryHandleOnlyNextAsgnExist(final AST ast, final ASTRewrite r, final IfStatement ifStmnt,
			final Assignment asgnThen, final Assignment nextAsgn, final VariableDeclarationFragment prevDecl) {
		if (!isOnlyNextAsgnPossible(asgnThen, nextAsgn))
			return false;
		if (prevDecl == null && !isAssignment(nextAsgn.getRightHandSide()))
			r.remove(ifStmnt, null);
		else if (prevDecl != null && isOpAssign(asgnThen)
				&& !dependsOn(prevDecl.getName(), nextAsgn.getRightHandSide())) {
			r.replace(prevDecl,
					makeVarDeclFrag(ast, r, (SimpleName) nextAsgn.getLeftHandSide(), nextAsgn.getRightHandSide()), null);
			r.remove(ifStmnt, null);
			r.remove(nextAsgn.getParent(), null);
		} else
			return handleNoPrevDecl(ast, r, ifStmnt, asgnThen, nextAsgn);
		return true;
	}

	private static boolean isOnlyNextAsgnPossible(final Assignment asgnThen, final Assignment nextAsgn) {
		return nextAsgn != null && compatible(nextAsgn, asgnThen)
				&& !isConditional(nextAsgn.getRightHandSide(), asgnThen.getRightHandSide())
				&& !asgnThen.getRightHandSide().toString().equals(nextAsgn.getRightHandSide().toString())
				&& !nextAsgn.getRightHandSide().toString().equals(asgnThen.getRightHandSide().toString());
	}

	private static boolean tryHandleOnlyPrevAsgnExist(final AST ast, final ASTRewrite r, final IfStatement ifStmnt,
			final Assignment asgnThen, final Assignment prevAsgn, final VariableDeclarationFragment prevDecl) {
		if (!isOnlyPrevAsgnPossible(ifStmnt, asgnThen, prevAsgn))
			return false;
		return prevDecl == null ? handleNoPrevDecl(ast, r, ifStmnt, asgnThen, prevAsgn) : handlePrevDeclExist(ast, r,
				ifStmnt, asgnThen, prevAsgn, prevDecl);
	}

	private static boolean isOnlyPrevAsgnPossible(final IfStatement ifStmnt, final Assignment asgnThen,
			final Assignment prevAsgn) {
		return prevAsgn != null && !dependsOn(prevAsgn.getLeftHandSide(), ifStmnt.getExpression())
				&& !isConditional(prevAsgn.getRightHandSide(), asgnThen.getRightHandSide())
				&& !isAssignment(prevAsgn.getRightHandSide()) && compatible(prevAsgn, asgnThen)
				&& !prevAsgn.getRightHandSide().toString().equals(asgnThen.getRightHandSide().toString());
	}

	private static boolean handlePrevDeclExist(final AST ast, final ASTRewrite r, final IfStatement ifStmnt,
			final Assignment asgnThen, final Assignment prevAsgn, final VariableDeclarationFragment prevDecl) {
		if (!dependsOn(prevDecl.getName(), asgnThen.getRightHandSide(), prevAsgn.getRightHandSide())
				&& isOpAssign(asgnThen)) {
			r.replace(
					prevDecl,
					makeVarDeclFrag(
							ast,
							r,
							(SimpleName) prevAsgn.getLeftHandSide(),
							makeParenthesizedConditionalExp(ast, r, ifStmnt.getExpression(),
									asgnThen.getRightHandSide(), prevAsgn.getRightHandSide())), null);
			r.remove(ifStmnt, null);
			r.remove(prevAsgn.getParent(), null);
			return true;
		} else if (null != prevDecl.getInitializer())
			return handleNoPrevDecl(ast, r, ifStmnt, asgnThen, prevAsgn);
		return false;
	}

	private static boolean handleNoPrevDecl(final AST ast, final ASTRewrite r, final IfStatement ifStmnt,
			final Assignment asgnThen, final Assignment prevAsgn) {
		rewriteAssignIfAssignToAssignTernary(ast, r, ifStmnt, asgnThen, prevAsgn.getRightHandSide());
		r.remove(prevAsgn.getParent(), null);
		return true;
	}

	private static boolean tryHandleNextAndPrevAsgnExist(final ASTRewrite r, final IfStatement ifStmnt,
			final Assignment asgnThen, final Assignment prevAsgn, final Assignment nextAsgn,
			final VariableDeclarationFragment prevDecl) {
		if (!isNextAndPrevAsgnPossible(asgnThen, prevAsgn, nextAsgn))
			return false;
		if (prevDecl == null)
			r.replace(prevAsgn.getParent(), nextAsgn.getParent(), null);
		else if (isOpAssign(asgnThen)) {
			r.replace(prevDecl.getInitializer(), nextAsgn.getRightHandSide(), null);
			r.remove(prevAsgn.getParent(), null);
		}
		r.remove(ifStmnt, null);
		r.remove(nextAsgn.getParent(), null);
		return true;
	}

	private static boolean isNextAndPrevAsgnPossible(final Assignment asgnThen, final Assignment prevAsgn,
			final Assignment nextAsgn) {
		return !hasNull(prevAsgn, nextAsgn)
				&& compatible(nextAsgn, prevAsgn, asgnThen)
				&& !isConditional(prevAsgn.getRightHandSide(), nextAsgn.getRightHandSide(), asgnThen.getRightHandSide());
	}

	private static VariableDeclarationFragment findPrevDecl(final List<ASTNode> stmts, final int ifIdx,
			final Assignment asgnThen, final Assignment prevAsgn, final Assignment nextAsgn) {
		VariableDeclarationFragment $ = null;
		if (prevAsgn != null && 0 <= ifIdx - 2
				&& compatibleNames(asgnThen.getLeftHandSide(), prevAsgn.getLeftHandSide()))
			$ = getVarDeclFrag(stmts.get(ifIdx - 2), asgnThen.getLeftHandSide());
		else if (nextAsgn == null && 0 <= ifIdx - 1)
			$ = getVarDeclFrag(stmts.get(ifIdx - 1), asgnThen.getLeftHandSide());
		else if (nextAsgn != null && 0 <= ifIdx - 1
				&& compatibleNames(asgnThen.getLeftHandSide(), nextAsgn.getLeftHandSide()))
			$ = getVarDeclFrag(stmts.get(ifIdx - 1), nextAsgn.getLeftHandSide());
		return $;
	}

	private static void rewriteAssignIfAssignToAssignTernary(final AST t, final ASTRewrite r,
			final IfStatement ifStmnt, final Assignment asgnThen, final Expression otherAsgnExp) {
		final Expression thenSideExp = isOpAssign(asgnThen) ? asgnThen.getRightHandSide() : makeInfixExpression(t, r,
				InfixExpression.Operator.PLUS, asgnThen.getRightHandSide(), otherAsgnExp);
		final Expression newCond = makeParenthesizedConditionalExp(t, r, ifStmnt.getExpression(), thenSideExp,
				otherAsgnExp);
		final Assignment newAsgn = makeAssigment(t, r, asgnThen.getOperator(), newCond, asgnThen.getLeftHandSide());
		r.replace(ifStmnt, t.newExpressionStatement(newAsgn), null);
	}

	static Range detectIfReturn(final IfStatement ifStmnt) {
		return null == statements(ifStmnt.getParent()) ? null
				: detectIfReturn(ifStmnt, statements(ifStmnt.getParent()));
	}

	private static Range detectIfReturn(final IfStatement ifStmnt, final List<ASTNode> ss) {
		final int ifIdx = ss.indexOf(ifStmnt);
		if (ss.size() <= ifIdx + 1)
			return null;
		final ReturnStatement nextRet = asReturn(ss.get(ifIdx + 1));
		if (nextRet == null || isConditional(nextRet.getExpression()))
			return null;
		final ReturnStatement thenSide = asReturn(ifStmnt.getThenStatement());
		final ReturnStatement elseSide = asReturn(ifStmnt.getElseStatement());
		return thenSide != null && elseSide == null && !isConditional(thenSide.getExpression()) || thenSide == null
				&& elseSide != null && !isConditional(elseSide.getExpression()) ? new Range(ifStmnt, nextRet) : null;
	}

	static Range detectIfSameExpStmntOrRet(final IfStatement ifStmnt) {
		final Statement thenStmnt = getBlockSingleStmnt(ifStmnt.getThenStatement());
		final Statement elseStmnt = getBlockSingleStmnt(ifStmnt.getElseStatement());
		if (hasNull(thenStmnt, elseStmnt, asBlock(ifStmnt.getParent())))
			return null;
		final List<TwoNodes> diffList = findDiffList(ifStmnt.getThenStatement(), ifStmnt.getElseStatement());
		return isDiffListValid(diffList) ? new Range(ifStmnt) : null;
	}

	private static Block asBlock(final ASTNode n) {
		return !(n instanceof Block) ? null : (Block) n;
	}

	static Range detectAssignIfAssign(final IfStatement ifStmnt) {
		return null == asBlock(ifStmnt.getParent()) ? null
				: detectAssignIfAssign(ifStmnt, asBlock(ifStmnt.getParent()));
	}

	private static Range detectAssignIfAssign(final IfStatement ifStmnt, final Block parent) {
		final List<ASTNode> stmts = parent.statements();
		final int ifIdx = stmts.indexOf(ifStmnt);
		if (ifIdx < 1 && stmts.size() <= ifIdx + 1)
			return null;
		final Assignment asgnThen = getAssignment(ifStmnt.getThenStatement());
		if (asgnThen == null || null != ifStmnt.getElseStatement())
			return null;
		final Assignment nextAsgn = getAssignment((Statement) stmts.get(ifIdx + 1 <= stmts.size() - 1 ? ifIdx + 1
				: stmts.size() - 1));
		final Assignment prevAsgn = getAssignment((Statement) stmts.get(0 > ifIdx - 1 ? 0 : ifIdx - 1));
		final VariableDeclarationFragment prevDecl = getVarDeclFrag(
				prevAsgn != null ? stmts.get(0 > ifIdx - 2 ? 0 : ifIdx - 2) : stmts.get(0 > ifIdx - 1 ? 0 : ifIdx - 1),
				asgnThen.getLeftHandSide());
		Range $ = detecPrevAndNextAsgnExist(asgnThen, prevAsgn, nextAsgn, prevDecl);
		if ($ != null)
			return $;
		$ = detecOnlyPrevAsgnExist(ifStmnt, asgnThen, prevAsgn, prevDecl);
		if ($ != null)
			return $;
		$ = detecOnlyNextAsgnExist(ifStmnt, asgnThen, nextAsgn, prevDecl);
		return $ != null ? $ : detecNoPrevNoNextAsgn(ifStmnt, asgnThen, prevAsgn, nextAsgn, prevDecl);
	}

	private static Range detecNoPrevNoNextAsgn(final IfStatement ifStmnt, final Assignment asgnThen,
			final Assignment prevAsgn, final Assignment nextAsgn, final VariableDeclarationFragment prevDecl) {
		return prevAsgn == null && nextAsgn == null && prevDecl != null && null != prevDecl.getInitializer()
				&& !dependsOn(prevDecl.getName(), ifStmnt.getExpression(), asgnThen.getRightHandSide()) ? new Range(
				prevDecl, ifStmnt) : null;
	}

	private static Range detecOnlyNextAsgnExist(final IfStatement ifStmnt, final Assignment asgnThen,
			final Assignment nextAsgn, final VariableDeclarationFragment prevDecl) {
		if (nextAsgn == null || !compatible(nextAsgn, asgnThen))
			return null;
		return prevDecl != null && !dependsOn(prevDecl.getName(), nextAsgn.getRightHandSide()) ? new Range(prevDecl,
				nextAsgn) : new Range(ifStmnt, nextAsgn);
	}

	private static Range detecOnlyPrevAsgnExist(final IfStatement ifStmnt, final Assignment asgnThen,
			final Assignment prevAsgn, final VariableDeclarationFragment prevDecl) {
		if (prevAsgn == null || dependsOn(prevAsgn.getLeftHandSide(), ifStmnt.getExpression())
				|| !compatible(prevAsgn, asgnThen))
			return null;
		if (prevDecl != null && null == prevDecl.getInitializer())
			return dependsOn(prevDecl.getName(), prevAsgn.getRightHandSide()) ? null : new Range(prevDecl, ifStmnt);
		return new Range(prevAsgn, ifStmnt);
	}

	private static Range detecPrevAndNextAsgnExist(final Assignment asgnThen, final Assignment prevAsgn,
			final Assignment nextAsgn, final VariableDeclarationFragment prevDecl) {
		if (hasNull(prevAsgn, nextAsgn) || !compatible(nextAsgn, prevAsgn, asgnThen))
			return null;
		if (prevDecl != null)
			return dependsOn(prevDecl.getName(), nextAsgn.getRightHandSide()) ? null : new Range(prevDecl, nextAsgn);
		return new Range(prevAsgn, nextAsgn);
	}

	private static boolean dependsOn(final Expression expToCheck, final Expression... possiblyDependentExps) {
		for (final Expression pde : possiblyDependentExps)
			if (0 < Occurrences.BOTH_SEMANTIC.of(expToCheck).in(pde).size())
				return true;
		return false;
	}

	private static boolean possibleToReplace(final Assignment asgn, final List<VariableDeclarationFragment> frags) {
		final int indexOfAsgn = findIndexOfAsgn(asgn.getLeftHandSide(), frags);
		if (indexOfAsgn < 0)
			return false;
		for (final VariableDeclarationFragment frag : frags)
			if (asgn.getRightHandSide().toString().equals(frag.getName().toString())
					&& (indexOfAsgn < frags.indexOf(frag) || indexOfAsgn != frags.indexOf(frag)
							&& null == frag.getInitializer()))
				return false;
		return true;
	}

	private static int findIndexOfAsgn(final Expression name, final List<VariableDeclarationFragment> frags) {
		for (final VariableDeclarationFragment frag : frags)
			if (frag.getName().toString().equals(name.toString()))
				return frags.indexOf(frag);
		return -1;
	}

	@Override protected ASTVisitor fillOpportunities(final List<Range> opportunities) {
		return new ASTVisitor() {
			@Override public boolean visit(final IfStatement i) {
				return //
				perhaps(detectAssignIfAssign(i)) || //
						perhaps(detectIfReturn(i)) || //
						perhaps(detectIfSameExpStmntOrRet(i)) || //
						true;
			}

			private boolean perhaps(final Range r) {
				return r != null && add(r);
			}

			private boolean add(final Range r) {
				opportunities.add(r);
				return true;
			}
		};
	}
}
