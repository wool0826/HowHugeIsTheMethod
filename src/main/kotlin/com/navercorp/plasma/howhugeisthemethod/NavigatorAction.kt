package com.navercorp.plasma.howhugeisthemethod

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.navercorp.plasma.howhugeisthemethod.core.NavigableNode
import com.navercorp.plasma.howhugeisthemethod.core.Navigator
import com.navercorp.plasma.howhugeisthemethod.factory.TreeTableFactory
import com.navercorp.plasma.howhugeisthemethod.util.getName

class NavigatorAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val psiJavaFile = e.getData(CommonDataKeys.PSI_FILE) as PsiJavaFile
        val caret = e.getData(CommonDataKeys.EDITOR)?.caretModel ?: return

        val psiElementOnCursor = psiJavaFile.findElementAt(caret.offset)

        val targetMethod = PsiTreeUtil.getParentOfType(psiElementOnCursor, PsiMethod::class.java)
        val targetClass = PsiTreeUtil.getParentOfType(psiElementOnCursor, PsiClass::class.java)

        if (targetMethod == null && targetClass == null) {
            Messages.showErrorDialog("Can't find (class/method) reference to calculate", "Error")
            return
        }

        val skipDialogResult = Messages.showYesNoCancelDialog(
            """
                There is step to select implementation class of inherited interface.
                
                If you want to skip this step by selecting first available one, select "Skip" button.
                
                If you choose to skip this step, the result may be approximate.
            """.trimIndent(),
            "Choose One",
            "Skip",
            "I Want To Check Them",
            "Cancel",
            Messages.getQuestionIcon()
        )

        if (skipDialogResult == Messages.CANCEL) {
            return
        }

        // tree traversal
        val targetNode = NavigableNode(targetMethod ?: requireNotNull(targetClass))

        val navigator =
            Navigator(requireNotNull(e.project), targetNode.psiElement, targetNode, skipDialogResult == Messages.OK)
        val rootNode = navigator.navigate()

        // add Component to ToolWindow
        val toolWindow =
            ToolWindowManager.getInstance(requireNotNull(e.project)).getToolWindow("NavigatorWindow") ?: return

        val contentFactory = toolWindow.contentManager.factory
        val treeTableFactory = TreeTableFactory()

        val content = contentFactory.createContent(
            treeTableFactory.buildJBTreeTable(rootNode),
            rootNode.psiElement.getName(),
            false
        )

        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
        toolWindow.show()
    }
}
