package com.navercorp.plasma.howhugeisthemethod.core

import com.intellij.psi.PsiElement
import com.navercorp.plasma.howhugeisthemethod.util.toSignatureString

// TODO: 코루틴사용하도록 수정, List, Int 모두 변경해야할 것으로 판단됨.
data class NavigableNode(
    val psiElement: PsiElement,
    val children: MutableList<NavigableNode> = mutableListOf(),
    var numberOfStatement: Int = 0,
    val skipped: Boolean = false,
) {
    fun appendChild(childNode: NavigableNode) {
        children.add(childNode)
    }

    private val numberOfStatementInChildrenNodes = lazy {
        children.sumOf { it.getTotalNumberOfStatement() }
    }

    fun getTotalNumberOfStatement(): Int {
        return numberOfStatementInChildrenNodes.value + numberOfStatement
    }

    override fun toString(): String {
        return psiElement.toSignatureString()
    }
}
