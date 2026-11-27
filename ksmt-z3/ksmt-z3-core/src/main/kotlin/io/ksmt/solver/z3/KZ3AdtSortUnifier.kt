package io.ksmt.solver.z3

import com.microsoft.z3.Native
import com.microsoft.z3.enumerations.Z3_sort_kind
import com.microsoft.z3.getArraySortDomain
import io.ksmt.KContext
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArray3Sort
import io.ksmt.sort.KArrayNSort
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KArraySortBase
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KFpRoundingModeSort
import io.ksmt.sort.KFpSort
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import io.ksmt.sort.KSort
import io.ksmt.sort.KSortVisitor
import io.ksmt.sort.KUninterpretedSort
import io.ksmt.sort.adt.KAdtDefinition
import io.ksmt.sort.adt.KAdtSort
import io.ksmt.sort.adt.KAdtTypeParameterSort
import io.ksmt.sort.substituteSorts

internal class KZ3AdtSortUnifier(
    private val ctx: KContext,
    private val z3Ctx: KZ3Context,
    private val converter: KZ3ExprConverter,
    private val internalizer: KZ3ExprInternalizer
) : KSortVisitor<Boolean> {
    private val nCtx: Long = z3Ctx.nCtx

    val mapping = hashMapOf<KAdtTypeParameterSort, KSort>()
    private val visited = hashSetOf<Pair<KSort, Int>>()
    private var nativeSort: Long = 0L

    fun unify(template: KSort, sort: Long): Boolean {
        val previous = nativeSort
        nativeSort = sort
        try {
            return template.accept(this)
        } finally {
            nativeSort = previous
        }
    }

    fun unifyAdtFields(definition: KAdtDefinition, args: List<KSort>, sort: Long): Boolean {
        if (!sort.hasKind(Z3_sort_kind.Z3_DATATYPE_SORT)) {
            return false
        }
        if (Native.getDatatypeSortNumConstructors(nCtx, sort) != definition.constructors.size) {
            return false
        }
        val substitution = definition.typeParameters.zip(args).toMap()

        definition.constructors.forEachIndexed { ctorIdx, ctor ->
            val nativeCtor = Native.getDatatypeSortConstructor(nCtx, sort, ctorIdx)
            if (Native.getDomainSize(nCtx, nativeCtor) != ctor.fields.size) {
                return false
            }
            ctor.fields.forEachIndexed { fieldIdx, field ->
                val template = ctx.substituteSorts(field.sort, substitution)
                if (!unify(template, Native.getDomain(nCtx, nativeCtor, fieldIdx))) {
                    return false
                }
            }
        }
        return true
    }

    private fun Long.hasKind(kind: Z3_sort_kind): Boolean = Native.getSortKind(nCtx, this) == kind.toInt()

    private fun isEqual(sort: KSort): Boolean {
        val internalized = with(internalizer) { sort.internalizeSort() }
        return Native.isEqSort(nCtx, internalized, nativeSort)
    }

    private fun unifyArray(sort: KArraySortBase<*>): Boolean {
        if (!nativeSort.hasKind(Z3_sort_kind.Z3_ARRAY_SORT)) {
            return false
        }
        val current = nativeSort
        val nativeDomain = getArraySortDomain(nCtx, current)
        if (nativeDomain.size != sort.domainSorts.size) {
            return false
        }
        return sort.domainSorts.zip(nativeDomain).all { (t, n) -> unify(t, n) }
            && unify(sort.range, Native.getArraySortRange(nCtx, current))
    }

    override fun visit(sort: KBoolSort): Boolean = isEqual(sort)

    override fun visit(sort: KIntSort): Boolean = isEqual(sort)

    override fun visit(sort: KRealSort): Boolean = isEqual(sort)

    override fun <S : KBvSort> visit(sort: S): Boolean = isEqual(sort)

    override fun <S : KFpSort> visit(sort: S): Boolean = isEqual(sort)

    override fun visit(sort: KFpRoundingModeSort): Boolean = isEqual(sort)

    override fun visit(sort: KUninterpretedSort): Boolean = isEqual(sort)

    override fun <D : KSort, R : KSort> visit(sort: KArraySort<D, R>): Boolean = unifyArray(sort)

    override fun <D0 : KSort, D1 : KSort, R : KSort> visit(sort: KArray2Sort<D0, D1, R>): Boolean =
        unifyArray(sort)

    override fun <D0 : KSort, D1 : KSort, D2 : KSort, R : KSort> visit(
        sort: KArray3Sort<D0, D1, D2, R>
    ): Boolean = unifyArray(sort)

    override fun <R : KSort> visit(sort: KArrayNSort<R>): Boolean = unifyArray(sort)

    override fun visit(sort: KAdtTypeParameterSort): Boolean {
        val converted: KSort = with(converter) { nativeSort.convertSort() }
        val assigned: KSort? = mapping.putIfAbsent(sort, converted)
        return assigned == null || assigned == converted
    }

    override fun visit(sort: KAdtSort): Boolean {
        if (!sort.definition.isParametric) {
            return isEqual(sort)
        }
        val current = nativeSort
        if (!visited.add(sort to Native.getSortId(nCtx, current))) {
            return true
        }
        if (!current.hasKind(Z3_sort_kind.Z3_DATATYPE_SORT)) {
            return false
        }
        val nativeName = converter.convertNativeSymbol(Native.getSortName(nCtx, current))
        if (nativeName != z3Ctx.datatypes.nativeName(sort.definition)) {
            return false
        }
        return unifyAdtFields(sort.definition, sort.args, current)
    }
}
