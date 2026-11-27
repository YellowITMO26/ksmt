package io.ksmt.sort

import io.ksmt.KContext
import io.ksmt.sort.adt.KAdtSort
import io.ksmt.sort.adt.KAdtTypeParameterSort

open class KSortSubstitutor(
    val ctx: KContext,
    private val substitution: Map<out KSort, KSort>
) : KSortVisitor<KSort> {

    fun apply(sort: KSort): KSort {
        if (substitution.isEmpty()) {
            return sort
        }
        return substitution[sort] ?: sort.accept(this)
    }

    override fun visit(sort: KBoolSort): KSort = sort

    override fun visit(sort: KIntSort): KSort = sort

    override fun visit(sort: KRealSort): KSort = sort

    override fun <S : KBvSort> visit(sort: S): KSort = sort

    override fun <S : KFpSort> visit(sort: S): KSort = sort

    override fun visit(sort: KFpRoundingModeSort): KSort = sort

    override fun visit(sort: KUninterpretedSort): KSort = sort

    override fun visit(sort: KAdtTypeParameterSort): KSort = sort

    override fun <D : KSort, R : KSort> visit(sort: KArraySort<D, R>): KSort =
        ctx.mkArraySort(apply(sort.domain), apply(sort.range))

    override fun <D0 : KSort, D1 : KSort, R : KSort> visit(sort: KArray2Sort<D0, D1, R>): KSort =
        ctx.mkArraySort(apply(sort.domain0), apply(sort.domain1), apply(sort.range))

    override fun <D0 : KSort, D1 : KSort, D2 : KSort, R : KSort> visit(sort: KArray3Sort<D0, D1, D2, R>): KSort =
        ctx.mkArraySort(apply(sort.domain0), apply(sort.domain1), apply(sort.domain2), apply(sort.range))

    override fun <R : KSort> visit(sort: KArrayNSort<R>): KSort =
        ctx.mkArrayNSort(sort.domainSorts.map { apply(it) }, apply(sort.range))

    override fun visit(sort: KAdtSort): KSort =
        if (sort.args.isEmpty()) {
            sort
        } else {
            ctx.mkAdtSort(sort.definition, sort.args.map { apply(it) })
        }
}

fun KContext.substituteSorts(sort: KSort, substitution: Map<out KSort, KSort>): KSort =
    KSortSubstitutor(this, substitution).apply(sort)
