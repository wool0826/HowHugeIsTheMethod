package com.navercorp.plasma.howhugeisthemethod.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiType
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.navercorp.plasma.howhugeisthemethod.type.NavigateResultType
import com.navercorp.plasma.howhugeisthemethod.util.createNodeAndAttachTo

data class Navigator(
    val project: Project,
    val rootElement: PsiElement?,
    val rootNode: NavigableNode,
    val selectStepSkipped: Boolean
) {
    private val rootManager = PsiManager.getInstance(project)
    private val implementationClasses = mutableMapOf<PsiClass, PsiClass>() // interface - implementation class
    private val visitedMethods = mutableSetOf<PsiMethod>()

    fun navigate(): NavigableNode {
        navigate(rootElement, rootNode)
        return rootNode
    }

    private fun navigate(element: PsiElement?, parent: NavigableNode) {
        if (element == null) {
            return
        }

        when (element) {
            is PsiMethod -> {
                if (!visitedMethods.add(element)) {
                    // skip traversal if the element has already been calculated
                    element.createNodeAndAttachTo(parent, skipped = true)
                    return
                }

                navigateAll(element.children, parent)
            }

            is PsiClass -> {
                element.methods.forEach { method ->
                    // clear visit history, when traverse a new method
                    if (element !is PsiAnonymousClass) {
                        visitedMethods.clear()
                    }

                    method
                        .createNodeAndAttachTo(parent)
                        .also { navigate(method, it) }
                }
            }

            is PsiMethodCallExpression -> {
                val navigateResultType = navigatePsiMethodCallExpression(element, parent)

                if (navigateResultType == NavigateResultType.NONE) {
                    navigateAll(element.children, parent)
                }
            }

            is PsiMethodReferenceExpression -> {
                val resolvedElement = element.resolve()

                if (resolvedElement is PsiMethod) {
                    resolvedElement
                        .createNodeAndAttachTo(parent)
                        .also { navigate(resolvedElement, it) }

                    return
                }

                navigateAll(element.children, parent)
            }

            is PsiCodeBlock -> {
                parent.numberOfStatement += element.statementCount
                navigateAll(element.children, parent)
            }

            else -> {
                navigateAll(element.children, parent)
            }
        }
    }

    private fun navigateAll(psiElementList: Array<out PsiElement>, parentNode: NavigableNode) {
        psiElementList.forEach { navigate(it, parentNode) }
    }

    private fun navigatePsiMethodCallExpression(
        element: PsiMethodCallExpression,
        parent: NavigableNode,
    ): NavigateResultType {
        val resolvedMethodFromExpression = element.resolveMethod() ?: return NavigateResultType.NONE
        val resolvedClassFromMethod = resolvedMethodFromExpression.containingClass ?: return NavigateResultType.NONE

        // navigate only in-project classes
        if (!rootManager.isInProject(resolvedClassFromMethod)) {
            return NavigateResultType.NONE
        }

        if (!visitedMethods.add(resolvedMethodFromExpression)) {
            resolvedMethodFromExpression.createNodeAndAttachTo(parent, skipped = true)
            return NavigateResultType.SKIPPED
        }

        if (!resolvedClassFromMethod.isInterface) {
            resolvedMethodFromExpression
                .createNodeAndAttachTo(parent)
                .also { navigateAll(resolvedMethodFromExpression.children, it) }

            return NavigateResultType.NAVIGATE_ALL
        }

        // use previous result of finding implementation class
        if (implementationClasses[resolvedClassFromMethod] != null) {
            val invokedOverrideMethod =
                implementationClasses[resolvedClassFromMethod]!!.findMethodBySignature(
                    resolvedMethodFromExpression,
                    true,
                ) ?: return NavigateResultType.NONE

            invokedOverrideMethod
                .createNodeAndAttachTo(parent)
                .also { navigate(invokedOverrideMethod, it) }

            return NavigateResultType.NAVIGATE
        }

        // find implementation classes of Interface in local scope
        val classInLocalScope = getDeclaredClassInFieldOrParameter(element)
        if (classInLocalScope != null) {
            implementationClasses[resolvedClassFromMethod] = classInLocalScope

            val invokedOverrideMethod = classInLocalScope.findMethodBySignature(resolvedMethodFromExpression, true)
                ?: return NavigateResultType.NONE

            invokedOverrideMethod
                .createNodeAndAttachTo(parent)
                .also { navigate(invokedOverrideMethod, it) }

            return NavigateResultType.NAVIGATE
        }

        // find implementation classes of Interface in global scope
        val classesInGlobalScope = getImplementationClasses(resolvedClassFromMethod)
        if (classesInGlobalScope.isEmpty()) {
            return NavigateResultType.NONE
        }

        val selectedIndex =
            if (selectStepSkipped || classesInGlobalScope.size == 1) {
                0
            } else {
                Messages.showChooseDialog(
                    project,
                    """
                        Select Implementation class of ${resolvedClassFromMethod.name}

                        target method:
                        ${resolvedMethodFromExpression.text}
                    """.trimStart(),
                    "Select Implementation Class",
                    Messages.getQuestionIcon(),
                    classesInGlobalScope.map { it.name }.toTypedArray(),
                    classesInGlobalScope.first().name
                )
            }

        if (selectedIndex == -1) {
            return NavigateResultType.NONE
        }

        implementationClasses[resolvedClassFromMethod] = classesInGlobalScope[selectedIndex]

        val invokedOverrideMethod = findMethodInClass(classesInGlobalScope[selectedIndex], resolvedMethodFromExpression)
            ?: return NavigateResultType.NONE

        invokedOverrideMethod
            .createNodeAndAttachTo(parent)
            .also { navigate(invokedOverrideMethod, it) }

        return NavigateResultType.NAVIGATE
    }

    private fun getDeclaredClassInFieldOrParameter(expression: PsiMethodCallExpression): PsiClass? {
        val declaredClassInMethodParameters =
            PsiTypesUtil.getPsiClass(getPsiTypeIfExpressionQualifierInMethodParameters(expression))

        if (declaredClassInMethodParameters != null && !declaredClassInMethodParameters.isInterface) {
            return declaredClassInMethodParameters
        }

        val declaredClassInParentClassFields =
            PsiTypesUtil.getPsiClass(getPsiTypeIfExpressionQualifierInClassFields(expression))

        if (declaredClassInParentClassFields != null && !declaredClassInParentClassFields.isInterface) {
            return declaredClassInParentClassFields
        }

        return null
    }

    private fun getPsiTypeIfExpressionQualifierInMethodParameters(expression: PsiMethodCallExpression): PsiType? {
        val method = PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java) ?: return null
        val qualifierInExpression = getQualifier(expression)

        return (method.parameters.find { it.name == qualifierInExpression }?.type) as PsiType?
    }

    private fun getQualifier(expression: PsiMethodCallExpression): String? {
        return expression.methodExpression.qualifier?.text?.replace("this.", "") // TODO: 깔끔한 방식이 있을텐데..
    }

    private fun getPsiTypeIfExpressionQualifierInClassFields(expression: PsiMethodCallExpression): PsiType? {
        val clazz = PsiTreeUtil.getParentOfType(expression, PsiClass::class.java) ?: return null
        val qualifierInExpression = getQualifier(expression)
        val field = clazz.fields.find { it.name == qualifierInExpression } ?: return null

        val newExpression = PsiTreeUtil.findChildOfType(field, PsiNewExpression::class.java)

        return newExpression?.type ?: field.type
    }

    private fun getImplementationClasses(psiClass: PsiClass): List<PsiClass> {
        return ClassInheritorsSearch.search(psiClass).findAll()
            .filterNotNull()
            .sortedBy { it.name }
            .toList()
    }

    private fun findMethodInClass(psiClass: PsiClass, invokedMethod: PsiMethod): PsiMethod? {
        val overridePsiMethod = psiClass.methods.find { it.findSuperMethods().contains(invokedMethod) }

        if (overridePsiMethod != null) {
            return overridePsiMethod
        }

        return psiClass.findMethodBySignature(invokedMethod, true)
    }
}
