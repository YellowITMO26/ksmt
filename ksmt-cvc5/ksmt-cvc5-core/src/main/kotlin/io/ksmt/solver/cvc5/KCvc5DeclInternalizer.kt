package io.ksmt.solver.cvc5

import io.github.cvc5.Datatype
import io.github.cvc5.DatatypeConstructor
import io.github.cvc5.DatatypeSelector
import io.github.cvc5.Solver
import io.github.cvc5.Term
import io.ksmt.decl.KAdtCtorDecl
import io.ksmt.decl.KAdtSelectorDecl
import io.ksmt.decl.KAdtTesterDecl
import io.ksmt.decl.KAdtUpdateFieldDecl
import io.ksmt.decl.KConstDecl
import io.ksmt.decl.KDeclVisitor
import io.ksmt.decl.KFuncDecl
import io.ksmt.sort.KSort
import io.ksmt.sort.adt.KAdtSort

open class KCvc5DeclInternalizer(
    private val cvc5Ctx: KCvc5Context,
    private val solver: Solver,
    private val sortInternalizer: KCvc5SortInternalizer
) : KDeclVisitor<Term> {
    private val tm = cvc5Ctx.termManager

    override fun <S : KSort> visit(decl: KFuncDecl<S>): Term = cvc5Ctx.internalizeDecl(decl) {
        // declarations incremental collection optimization
        cvc5Ctx.addDeclaration(decl)

        val domainSorts = decl.argSorts.map { it.accept(sortInternalizer) }
        val rangeSort = decl.sort.accept(sortInternalizer)

        solver.declareFun(
            decl.name,
            domainSorts.toTypedArray(),
            rangeSort
        ).also { tm.registerPointer(it) }
    }

    override fun <S : KSort> visit(decl: KConstDecl<S>): Term = cvc5Ctx.internalizeDecl(decl) {
        cvc5Ctx.addDeclaration(decl)

        val sort = decl.sort.accept(sortInternalizer)
        tm.builder { mkConst(sort, decl.name) }
    }

    override fun visit(decl: KAdtCtorDecl): Term = cvc5Ctx.internalizeDecl(decl) {
        val ctor = nativeConstructor(decl.adtSort, decl.constructorIndex)
        if (decl.adtSort.args.isEmpty()) {
            tm.registerPointer(ctor.term)
        } else {
            val sort = decl.adtSort.accept(sortInternalizer)
            tm.registerPointer(ctor.getInstantiatedTerm(sort))
        }
    }

    override fun <S : KSort> visit(decl: KAdtSelectorDecl<S>): Term = cvc5Ctx.internalizeDecl(decl) {
        tm.registerPointer(nativeSelector(decl.adtSort, decl.constructorIndex, decl.fieldIndex).term)
    }

    override fun visit(decl: KAdtTesterDecl): Term = cvc5Ctx.internalizeDecl(decl) {
        tm.registerPointer(nativeConstructor(decl.adtSort, decl.constructorIndex).testerTerm)
    }

    override fun <S : KSort> visit(decl: KAdtUpdateFieldDecl<S>): Term = cvc5Ctx.internalizeDecl(decl) {
        tm.registerPointer(nativeSelector(decl.adtSort, decl.constructorIndex, decl.fieldIndex).updaterTerm)
    }

    private fun nativeDatatype(sort: KAdtSort): Datatype {
        val nativeSort = sort.accept(sortInternalizer)
        return tm.sortOp(nativeSort) { datatype }
    }

    private fun nativeConstructor(sort: KAdtSort, constructorIndex: Int): DatatypeConstructor =
        tm.registerPointer(nativeDatatype(sort).getConstructor(constructorIndex))

    private fun nativeSelector(sort: KAdtSort, constructorIndex: Int, fieldIndex: Int): DatatypeSelector =
        tm.registerPointer(nativeConstructor(sort, constructorIndex).getSelector(fieldIndex))
}
