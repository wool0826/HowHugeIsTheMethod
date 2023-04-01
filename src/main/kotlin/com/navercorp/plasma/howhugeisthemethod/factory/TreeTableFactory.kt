package com.navercorp.plasma.howhugeisthemethod.factory

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.ui.components.JBTreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import com.navercorp.plasma.howhugeisthemethod.core.NavigableNode
import org.jdesktop.swingx.treetable.DefaultMutableTreeTableNode
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode

class TreeTableFactory {
    fun buildJBTreeTable(node: NavigableNode): JBTreeTable {
        val treeTable = JBTreeTable(
            CustomizedTreeTableModel(
                buildTree(parent = DefaultMutableTreeTableNode("root"), targetNode = node)
            )
        )

        treeTable.tree.selectionModel.addTreeSelectionListener(CustomizedTreeSelectionModel())
        return treeTable
    }

    private fun buildTree(parent: DefaultMutableTreeTableNode, targetNode: NavigableNode): DefaultMutableTreeTableNode {
        val currentTreeNode = DefaultMutableTreeTableNode(targetNode)

        targetNode.children.forEach { child ->
            currentTreeNode.add(buildTree(currentTreeNode, child))
        }

        parent.add(currentTreeNode)
        return parent
    }
}

class CustomizedTreeTableModel(root: TreeNode) : DefaultTreeModel(root), TreeTableModel {
    override fun getColumnCount(): Int {
        return columns.size
    }

    override fun getColumnName(column: Int): String {
        return columns[column].name
    }

    override fun getValueAt(node: Any, column: Int): Any {
        return columns[column].valueOf(node)
            ?: throw IllegalArgumentException("node: $node, columnIndex: $column")
    }

    override fun getChildCount(parent: Any): Int {
        return (parent as TreeNode).childCount
    }

    override fun getChild(parent: Any, index: Int): Any {
        return (parent as TreeNode).getChildAt(index)
    }

    override fun getColumnClass(column: Int): Class<*> {
        return columns[column].columnClass
    }

    override fun isCellEditable(node: Any, column: Int): Boolean {
        return false
    }

    override fun setValueAt(aValue: Any, node: Any, column: Int) {
        columns[column].setValue(node, aValue)
    }

    override fun setTree(tree: JTree) {}

    companion object {
        val columns = arrayOf(
            object : ColumnInfo<Any, Any?>("Skipped") {
                override fun valueOf(item: Any?): Any? {
                    if (item == null || item !is DefaultMutableTreeTableNode) {
                        return null
                    }

                    return if ((item.userObject as NavigableNode).skipped) "true" else ""
                }
            },
            object : ColumnInfo<Any, Any?>("Total") {
                override fun valueOf(item: Any?): Any? {
                    if (item == null || item !is DefaultMutableTreeTableNode) {
                        return null
                    }

                    return (item.userObject as NavigableNode).getTotalNumberOfStatement().toString()
                }
            },
            object : ColumnInfo<Any, Any?>("Self") {
                override fun valueOf(item: Any?): Any? {
                    if (item == null || item !is DefaultMutableTreeTableNode) {
                        return null
                    }

                    return (item.userObject as NavigableNode).numberOfStatement.toString()
                }
            }
        )
    }
}

class CustomizedTreeSelectionModel : TreeSelectionListener {
    override fun valueChanged(e: TreeSelectionEvent?) {
        val selectedNode = (e?.path?.lastPathComponent as DefaultMutableTreeTableNode)
        val psiElement = (selectedNode.userObject as NavigableNode).psiElement

        val editor = FileEditorManager.getInstance(psiElement.project).openTextEditor(
            OpenFileDescriptor(
                psiElement.project,
                psiElement.containingFile.virtualFile,
                psiElement.textOffset
            ),
            true,
        )

        editor?.caretModel?.moveToOffset(psiElement.textOffset)
        editor?.selectionModel?.removeSelection()
        editor?.contentComponent?.requestFocus()
    }
}
