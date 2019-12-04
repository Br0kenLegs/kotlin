/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.scopes.FirOverrideChecker
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassifierSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.name.Name

class FirSuperTypeScope private constructor(
    session: FirSession,
    overrideChecker: FirOverrideChecker,
    val scopes: List<FirScope>
) : AbstractFirOverrideScope(session, overrideChecker) {

    private val absentFunctions = mutableSetOf<Name>()

    private val absentProperties = mutableSetOf<Name>()

    private val absentClassifiers = mutableSetOf<Name>()

    override fun processFunctionsByName(name: Name, processor: (FirFunctionSymbol<*>) -> ProcessorAction): ProcessorAction {
        processCallablesByName(name, processor, absentFunctions, FirScope::processFunctionsByName)?.let { return it }
        return super.processFunctionsByName(name, processor)
    }

    override fun processPropertiesByName(name: Name, processor: (FirCallableSymbol<*>) -> ProcessorAction): ProcessorAction {
        processCallablesByName(name, processor, absentProperties, FirScope::processPropertiesByName)?.let { return it }
        return super.processPropertiesByName(name, processor)
    }

    private inline fun <D : FirCallableSymbol<*>> processCallablesByName(
        name: Name,
        noinline processor: (D) -> ProcessorAction,
        absentNames: MutableSet<Name>,
        processCallables: FirScope.(Name, (D) -> ProcessorAction) -> ProcessorAction
    ): ProcessorAction? {
        if (name in absentNames) {
            return ProcessorAction.NEXT
        }

        val accepted = HashSet<FirCallableSymbol<*>>()
        val pending = mutableListOf<FirCallableSymbol<*>>()
        var empty = true
        for (scope in scopes) {
            if (scope.processCallables(name) {
                    empty = false
                    if (it !in accepted && it.getOverridden(accepted) == null) {
                        pending += it
                        processor(it)
                    } else {
                        ProcessorAction.NEXT
                    }
                }.stop()
            ) {
                return ProcessorAction.STOP
            }
            accepted += pending
            pending.clear()
        }
        if (empty) {
            absentNames += name
        }

        return null
    }

    override fun processClassifiersByName(name: Name, processor: (FirClassifierSymbol<*>) -> ProcessorAction): ProcessorAction {
        if (name in absentClassifiers) {
            return ProcessorAction.NEXT
        }
        val accepted = HashSet<FirClassifierSymbol<*>>()
        val pending = mutableListOf<FirClassifierSymbol<*>>()
        var empty = true
        for (scope in scopes) {
            if (scope.processClassifiersByName(name) {
                    empty = false
                    if (it !in accepted) {
                        pending += it
                        processor(it)
                    } else {
                        ProcessorAction.NEXT
                    }
                }.stop()
            ) {
                return ProcessorAction.STOP
            }
            accepted += pending
            pending.clear()
        }
        if (empty) {
            absentClassifiers += name
        }
        return super.processClassifiersByName(name, processor)
    }

    companion object {
        fun prepareSupertypeScope(
            session: FirSession,
            overrideChecker: FirOverrideChecker,
            scopes: List<FirScope>
        ): FirScope {
            scopes.singleOrNull()?.let { return it }

            return FirSuperTypeScope(session, overrideChecker, scopes)
        }
    }
}
