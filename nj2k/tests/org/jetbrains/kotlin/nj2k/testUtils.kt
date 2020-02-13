/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.actions.JavaToKotlinAction
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.j2k.FilesResult
import org.jetbrains.kotlin.nj2k.postProcessing.NewJ2kPostProcessor
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import java.io.File

fun descriptorByFileDirective(testDataFile: File, isAllFilesPresentInTest: Boolean) =
    object : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        private fun projectDescriptorByFileDirective(): LightProjectDescriptor {
            if (isAllFilesPresentInTest) return INSTANCE
            val fileText = FileUtil.loadFile(testDataFile, true)
            return if (InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_FULL_JDK"))
                INSTANCE_FULL_JDK
            else INSTANCE
        }

        override fun getSdk(): Sdk? {
            val sdk = projectDescriptorByFileDirective().sdk ?: return null
            runWriteAction {
                val modificator: SdkModificator = sdk.sdkModificator
                JavaSdkImpl.attachJdkAnnotations(modificator)
                modificator.commitChanges()
            }
            return sdk
        }

        override fun configureModule(module: Module, model: ModifiableRootModel) {
            super.configureModule(module, model)
            model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_8
        }
    }

fun NewJavaToKotlinConverter.runJ2kInBackground(javaFiles: List<PsiJavaFile>, project: Project): FilesResult =
    project.executeCommand("Convert files from Java to Kotlin", null) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable {
                filesToKotlin(
                    javaFiles,
                    NewJ2kPostProcessor()
                )
            },
            JavaToKotlinAction.title,
            true,
            project
        )
    }