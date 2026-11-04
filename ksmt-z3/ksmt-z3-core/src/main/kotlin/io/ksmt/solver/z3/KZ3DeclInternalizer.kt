package io.ksmt.solver.z3

import com.microsoft.z3.Native
import io.ksmt.decl.KAdtCtorDecl
import io.ksmt.decl.KAdtSelectorDecl
import io.ksmt.decl.KAdtTesterDecl
import io.ksmt.decl.KAdtUpdateFieldDecl
import io.ksmt.decl.KConstDecl
import io.ksmt.decl.KDecl
import io.ksmt.decl.KDeclVisitor
import io.ksmt.decl.KFuncDecl
import io.ksmt.solver.util.KExprLongInternalizerBase.Companion.NOT_INTERNALIZED
import io.ksmt.sort.KSort

open class KZ3DeclInternalizer(
    private val z3Ctx: KZ3Context,
    private val sortInternalizer: KZ3SortInternalizer
) : KDeclVisitor<Unit> {
    private val nCtx = z3Ctx.nCtx
    private var lastInternalizedDecl: Long = NOT_INTERNALIZED

    override fun <S : KSort> visit(decl: KFuncDecl<S>) {
        val domainSorts = decl.argSorts.let { argSorts ->
            LongArray(argSorts.size) { sortInternalizer.internalizeZ3Sort(argSorts[it]) }
        }

        val rangeSort = sortInternalizer.internalizeZ3Sort(decl.sort)
        val nameSymbol = Native.mkStringSymbol(nCtx, decl.name)

        lastInternalizedDecl = Native.mkFuncDecl(
            nCtx,
            nameSymbol,
            domainSorts.size,
            domainSorts,
            rangeSort
        )
    }

    override fun <S : KSort> visit(decl: KConstDecl<S>) {
        val declSort = sortInternalizer.internalizeZ3Sort(decl.sort)
        val nameSymbol = Native.mkStringSymbol(nCtx, decl.name)

        lastInternalizedDecl = Native.mkFuncDecl(
            nCtx,
            nameSymbol,
            0,
            null,
            declSort
        )
    }

    override fun visit(decl: KAdtCtorDecl) {
        val sort = sortInternalizer.internalizeZ3Sort(decl.adtSort)
        lastInternalizedDecl = Native.getDatatypeSortConstructor(nCtx, sort, decl.constructorIndex)
    }

    override fun <S : KSort> visit(decl: KAdtSelectorDecl<S>) {
        val sort = sortInternalizer.internalizeZ3Sort(decl.adtSort)
        lastInternalizedDecl = Native.getDatatypeSortConstructorAccessor(
            nCtx, sort, decl.constructorIndex, decl.fieldIndex
        )
    }

    override fun visit(decl: KAdtTesterDecl) {
        val sort = sortInternalizer.internalizeZ3Sort(decl.adtSort)
        lastInternalizedDecl = Native.getDatatypeSortRecognizer(nCtx, sort, decl.constructorIndex)
    }

    override fun <S : KSort> visit(decl: KAdtUpdateFieldDecl<S>) {
        error("Datatype update-field declaration ${decl.name} can't be internalized standalone")
    }

    fun internalizeZ3Decl(decl: KDecl<*>): Long = z3Ctx.internalizeDecl(decl) {
        decl.accept(this@KZ3DeclInternalizer)
        lastInternalizedDecl
    }
}
