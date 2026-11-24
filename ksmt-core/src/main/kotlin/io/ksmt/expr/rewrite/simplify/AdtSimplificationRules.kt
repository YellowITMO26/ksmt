package io.ksmt.expr.rewrite.simplify

import io.ksmt.expr.KAdtConstructorApp
import io.ksmt.expr.KEqExpr
import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KSort
import io.ksmt.sort.adt.KAdtSort
import io.ksmt.utils.uncheckedCast

/**
 * Simplify equality of datatype values with [constructor clash detection][collectAdtEqLeaves]:
 * ```
 * (= (C a1 .. an) (C' b1 .. bn)) ==> false, C != C'
 * (= (C a1 .. an) (C b1 .. bn)) ==> (and (= a1 b1) .. (= an bn))
 * (= (C a (D b)) (C a' (D' b'))) ==> false
 * (= (C a (D b)) (C a' (D b'))) ==> (and (= a a') (= b b'))
 * ```
 * */
fun KExprSimplifier.simplifyEqAdt(
    lhs: KExpr<KAdtSort>,
    rhs: KExpr<KAdtSort>
): KExpr<KBoolSort> = with(ctx) {
    if (lhs == rhs) return trueExpr

    if (lhs !is KAdtConstructorApp || rhs !is KAdtConstructorApp) {
        return withExpressionsOrdered(lhs, rhs, ::mkEqNoSimplify)
    }

    val leaves = arrayListOf<Pair<KExpr<*>, KExpr<*>>>()
    if (!collectAdtEqLeaves(lhs, rhs, leaves)) return falseExpr

    if (leaves.size <= 1) {
        if (leaves.isEmpty()) return trueExpr
        val (l, r) = leaves.single()
        return rewrite(KEqExpr<KSort>(ctx, l.uncheckedCast(), r.uncheckedCast()))
    }

    return rewrite(auxExpr {
        mkAndAuxExpr(leaves.map { (l, r) -> KEqExpr<KSort>(ctx, l.uncheckedCast(), r.uncheckedCast()) })
    })
}

fun KExprSimplifier.areDefinitelyDistinctAdt(
    lhs: KExpr<KAdtSort>,
    rhs: KExpr<KAdtSort>
): Boolean = !collectAdtEqLeaves(lhs, rhs, arrayListOf())

private fun collectAdtEqLeaves(
    lhs: KExpr<*>,
    rhs: KExpr<*>,
    leaves: MutableList<Pair<KExpr<*>, KExpr<*>>>
): Boolean {
    if (lhs == rhs) return true

    val lhsCtor = lhs as? KAdtConstructorApp
    val rhsCtor = rhs as? KAdtConstructorApp
    if (lhsCtor == null || rhsCtor == null) {
        leaves += lhs to rhs
        return true
    }

    if (lhsCtor.decl.constructorIndex != rhsCtor.decl.constructorIndex) return false

    for ((lhsArg, rhsArg) in lhsCtor.args.zip(rhsCtor.args)) {
        if (!collectAdtEqLeaves(lhsArg, rhsArg, leaves)) return false
    }
    return true
}
