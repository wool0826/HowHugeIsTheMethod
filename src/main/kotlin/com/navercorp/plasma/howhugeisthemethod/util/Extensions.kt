package com.navercorp.plasma.howhugeisthemethod.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.navercorp.plasma.howhugeisthemethod.core.NavigableNode

fun PsiElement.toSignatureString(): String {
    return when (this) {
        is PsiMethod -> "${this.containingClass?.name}.${this.name}${this.parameterList.text}"
        is PsiClass -> "${this.name}"
        else -> throw IllegalArgumentException()
    }
}

fun PsiElement.getName(): String? {
    return when (this) {
        is PsiMethod -> this.name
        is PsiClass -> this.name
        else -> throw IllegalArgumentException()
    }
}

fun PsiMethod.createNodeAndAttachTo(parent: NavigableNode, skipped: Boolean = false): NavigableNode {
    val node = NavigableNode(this, skipped = skipped)
    parent.appendChild(node)

    return node
}
