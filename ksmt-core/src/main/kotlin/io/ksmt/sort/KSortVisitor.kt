package io.ksmt.sort

import io.ksmt.sort.adt.KAdtSort
import io.ksmt.sort.adt.KAdtTypeParameterSort

interface KSortVisitor<T> {
    fun visit(sort: KSort): Any = error("visitor is not implemented for sort $sort")
    fun visit(sort: KBoolSort): T
    fun visit(sort: KIntSort): T
    fun visit(sort: KRealSort): T
    fun <S : KBvSort> visit(sort: S): T
    fun <S : KFpSort> visit(sort: S): T
    fun <D : KSort, R : KSort> visit(sort: KArraySort<D, R>): T
    fun <D0 : KSort, D1 : KSort, R : KSort> visit(sort: KArray2Sort<D0, D1, R>): T
    fun <D0 : KSort, D1 : KSort, D2 : KSort, R : KSort> visit(sort: KArray3Sort<D0, D1, D2, R>): T
    fun <R : KSort> visit(sort: KArrayNSort<R>): T
    fun visit(sort: KFpRoundingModeSort): T
    fun visit(sort: KUninterpretedSort): T
    fun visit(sort: KAdtSort): T
    fun visit(sort: KAdtTypeParameterSort): T =
        error("Datatype type parameter $sort is not expected outside of datatype definitions")
}
