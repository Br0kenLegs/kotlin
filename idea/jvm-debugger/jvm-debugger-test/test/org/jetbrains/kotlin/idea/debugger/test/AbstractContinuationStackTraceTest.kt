/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.AsyncStackTraceProvider
import com.intellij.debugger.engine.JavaExecutionStack
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.ui.tree.nodes.XValuePresentationUtil
import kotlinx.coroutines.yield
import org.jetbrains.kotlin.idea.debugger.coroutine.util.formatLocation
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.utils.getSafe
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import javax.swing.Icon

abstract class AbstractContinuationStackTraceTest : KotlinDescriptorTestCaseWithStepping() {
    private companion object {
        const val MARGIN = "    "
    }

    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        doOnBreakpoint {
            val frameProxy = this.frameProxy
            if (frameProxy != null) {
                try {
                    val threadProxy = frameProxy.threadProxy()
                    val stackFrames = threadProxy.forceFrames()
                    val executionStack = JavaExecutionStack(threadProxy, this.debugProcess, true)
                    executionStack.initTopFrame()
                    println("Thread stack trace:", ProcessOutputTypes.SYSTEM)
                    for (frame in stackFrames) {
                        val stackFrame = executionStack.createStackFrame(frame)
                        if (stackFrame is JavaStackFrame) {
                            println(formatLocation(stackFrame.descriptor.location), ProcessOutputTypes.SYSTEM)
                            var variables = frame.visibleVariables()
                        }
                    }
                } catch (e: Throwable) {
                    val stackTrace = e.stackTraceAsString()
                    System.err.println("Exception occurred on calculating async stack traces: $stackTrace")
                    throw e
                }
            } else {
                println("FrameProxy is 'null', can't calculate async stack trace", ProcessOutputTypes.SYSTEM)
            }

            resume(this)
        }
    }

    private fun Throwable.stackTraceAsString(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun renderAsyncStackTrace(trace: List<StackFrameItem>) = buildString {
        appendln("Async stack trace:")
        for (item in trace) {
            append(MARGIN).appendln(item.toString())
            val declaredFields = listDeclaredFields(item.javaClass)

            @Suppress("UNCHECKED_CAST")
            val variablesField = declaredFields
                .first { !Modifier.isStatic(it.modifiers) && it.type == List::class.java }

            @Suppress("UNCHECKED_CAST")
            val variables = variablesField.getSafe(item) as? List<JavaValue>

            if (variables != null) {
                for (variable in variables) {
                    val descriptor = variable.descriptor
                    val name = descriptor.calcValueName()
                    val value = descriptor.calcValue(evaluationContext)

                    append(MARGIN).append(MARGIN).append(name).append(" = ").appendln(value)
                }
            }
        }
    }

    private fun listDeclaredFields(cls: Class<in Any>): MutableList<Field> {
        var clazz = cls
        val declaredFields = mutableListOf<Field>()
        while (clazz != Class.forName("java.lang.Object")) {
            declaredFields.addAll(clazz.declaredFields)
            clazz = clazz.superclass
        }
        return declaredFields
    }

    inner class TestValueNode(val name: String) : XValueNode {
        override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
            println("\t\tCalled setFullValueEvaluator", ProcessOutputTypes.SYSTEM)
        }

        override fun setPresentation(icon: Icon?, type: String?, value: String, hasChildren: Boolean) {
            println("\t\tCalled setPresentation: ${name} ${type} ${value}", ProcessOutputTypes.SYSTEM)
        }

        override fun setPresentation(icon: Icon?, presentation: XValuePresentation, hasChildren: Boolean) {
            val value = XValuePresentationUtil.computeValueText(presentation)
            println("\t\tCalled setPresentation: ${name} ${value}", ProcessOutputTypes.SYSTEM)
        }

        override fun setPresentation(
            icon: Icon?,
            type: String?,
            separator: String,
            value: String?,
            hasChildren: Boolean
        ) {
            println("\t\tCalled setPresentation: ${name} ${type} (${separator}) ${value}", ProcessOutputTypes.SYSTEM)
        }
    }
}


class TestCompositeNode(val f: (XValueChildrenList) -> Unit) : XCompositeNode {
    override fun setAlreadySorted(alreadySorted: Boolean) {
    }

    override fun tooManyChildren(remaining: Int) {
    }

    override fun setErrorMessage(errorMessage: String) {
    }

    override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) {
    }

    override fun addChildren(children: XValueChildrenList, last: Boolean) {
        f(children)
    }

    override fun setMessage(
        message: String,
        icon: Icon?,
        attributes: SimpleTextAttributes,
        link: XDebuggerTreeNodeHyperlink?
    ) {
    }
}
