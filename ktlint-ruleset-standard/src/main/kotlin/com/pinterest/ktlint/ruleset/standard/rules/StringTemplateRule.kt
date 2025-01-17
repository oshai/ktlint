package com.pinterest.ktlint.ruleset.standard.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLOSING_QUOTE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DOT_QUALIFIED_EXPRESSION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LITERAL_STRING_TEMPLATE_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LONG_STRING_TEMPLATE_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LONG_TEMPLATE_ENTRY_END
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LONG_TEMPLATE_ENTRY_START
import com.pinterest.ktlint.rule.engine.core.api.ElementType.REGULAR_STRING_PART
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.ruleset.standard.StandardRule
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression

public class StringTemplateRule : StandardRule("string-template") {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        val elementType = node.elementType
        // code below is commented out because (setting aside potentially dangerous replaceChild part)
        // `val v: String = "$elementType"` would be rewritten to `val v: String = elementType.toString()` and it's not
        // immediately clear which is better
        // if (elementType === SHORT_STRING_TEMPLATE_ENTRY || elementType == LONG_STRING_TEMPLATE_ENTRY) {
        //    if (node.treePrev.elementType == OPEN_QUOTE && node.treeNext.elementType == CLOSING_QUOTE) {
        //        emit(node.treePrev.startOffset, "Redundant string template", true)
        //        val entryStart = node.psi.firstChild
        //        node.treeParent.treeParent.replaceChild(node.treeParent, entryStart.nextSibling.node)
        //    }
        if (elementType == LONG_STRING_TEMPLATE_ENTRY) {
            var entryExpression = (node.psi as? KtBlockStringTemplateEntry)?.expression
            if (entryExpression is KtDotQualifiedExpression) {
                val receiver = entryExpression.receiverExpression
                if (entryExpression.selectorExpression?.text == "toString()" && receiver !is KtSuperExpression) {
                    emit(
                        entryExpression.operationTokenNode.startOffset,
                        "Redundant \"toString()\" call in string template",
                        true,
                    )
                    if (autoCorrect) {
                        entryExpression
                            .node
                            .let { entryExpressionNode ->
                                entryExpressionNode.treeParent.addChild(receiver.node, entryExpressionNode)
                                entryExpressionNode.treeParent.removeChild(entryExpressionNode)
                            }
                        node
                            .takeIf { it.isStringTemplate() }
                            ?.removeCurlyBracesIfRedundant()
                    }
                }
            }
            if (node.isStringTemplate() &&
                (entryExpression is KtNameReferenceExpression || entryExpression is KtThisExpression) &&
                node.treeNext.let { nextSibling ->
                    nextSibling.elementType == CLOSING_QUOTE ||
                        (
                            nextSibling.elementType == LITERAL_STRING_TEMPLATE_ENTRY &&
                                !nextSibling.text.substring(0, 1).isPartOfIdentifier()
                        )
                }
            ) {
                emit(node.treePrev.startOffset + 2, "Redundant curly braces", true)
                if (autoCorrect) {
                    node.removeCurlyBracesIfRedundant()
                }
            }
        }
    }

    private fun ASTNode.removeCurlyBracesIfRedundant() {
        if (isStringTemplate()) {
            val leftCurlyBraceNode = findChildByType(LONG_TEMPLATE_ENTRY_START)
            val rightCurlyBraceNode = findChildByType(LONG_TEMPLATE_ENTRY_END)
            if (leftCurlyBraceNode != null && rightCurlyBraceNode != null) {
                removeChild(leftCurlyBraceNode)
                removeChild(rightCurlyBraceNode)
                val remainingNode = firstChildNode
                val newNode =
                    if (remainingNode.elementType == DOT_QUALIFIED_EXPRESSION) {
                        LeafPsiElement(REGULAR_STRING_PART, "\$${remainingNode.text}")
                    } else {
                        LeafPsiElement(remainingNode.elementType, "\$${remainingNode.text}")
                    }
                replaceChild(firstChildNode, newNode)
            }
        }
    }

    private fun ASTNode.isStringTemplate() =
        text.startsWith("${'$'}{") &&
            text.substring(2, text.length - 1).isPartOfIdentifier()

    private fun String.isPartOfIdentifier() = this == "_" || this.all { it.isLetterOrDigit() }
}

public val STRING_TEMPLATE_RULE_ID: RuleId = StringTemplateRule().ruleId
