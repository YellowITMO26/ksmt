package io.ksmt.expr.rewrite.simplify

import io.ksmt.KContext
import io.ksmt.decl.KAdtCtorDecl
import io.ksmt.decl.KAdtSelectorDecl
import io.ksmt.decl.KAdtTesterDecl
import io.ksmt.decl.KAdtUpdateFieldDecl
import io.ksmt.expr.KAdtConstructorApp
import io.ksmt.expr.KAdtSelectorApp
import io.ksmt.expr.KAdtUpdateFieldApp
import io.ksmt.expr.KExpr
import io.ksmt.sort.adt.KAdtSort
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KSort
import io.ksmt.utils.uncheckedCast

fun KContext.simplifyAdtConstructorApp(
    constructor: KAdtCtorDecl,
    args: List<KExpr<*>>
): KExpr<KAdtSort> {
    // (C (sel_1 x) .. (sel_n x)) ==> x, C is the only constructor of the sort
    if (isSelectorCycle(constructor, args)) {
        return args.first().uncheckedCast<KExpr<*>, KAdtSelectorApp<KSort>>().value
    }

    return mkAdtConstructorAppNoSimplify(constructor, args)
}

private fun isSelectorCycle(constructor: KAdtCtorDecl, args: List<KExpr<*>>): Boolean {
    if (args.isEmpty() || constructor.adtSort.definition.constructors.size != 1) return false

    val selectorValue = args.first() as? KAdtSelectorApp<*> ?: return false
    return args.withIndex().all { (i, arg) ->
        arg is KAdtSelectorApp<*> &&
            arg.decl.constructorIndex == constructor.constructorIndex &&
            arg.decl.fieldIndex == i &&
            arg.value == selectorValue.value
    }
}

fun <T : KSort> KContext.simplifyAdtSelectorApp(
    selector: KAdtSelectorDecl<T>,
    value: KExpr<KAdtSort>
): KExpr<T> = with(selector) {
    // (sel_i (C a1 .. an)) ==> ai
    if (value is KAdtConstructorApp) {
        if (value.decl.constructorIndex != constructorIndex) return mkAdtSelectorAppNoSimplify(selector, value)
        return value.args[fieldIndex].uncheckedCast()
    }

    // (sel_i ((_ update-field s_i v) x)) ==> v
    if (value is KAdtUpdateFieldApp<*> && adtSort.definition.constructors.size == 1) {
        if (value.decl.constructorIndex == constructorIndex && value.decl.fieldIndex == fieldIndex) {
            return value.fieldValue.uncheckedCast()
        }
    }

    return mkAdtSelectorAppNoSimplify(selector, value)
}

fun KContext.simplifyAdtTesterApp(
    tester: KAdtTesterDecl,
    value: KExpr<KAdtSort>
): KExpr<KBoolSort> {
    // (_ is C) x ==> true, C is the only constructor of the sort
    if (tester.adtSort.definition.constructors.size == 1) return trueExpr

    // (_ is C) (C' a1 .. ak) ==> C == C'
    if (value is KAdtConstructorApp) {
        return (value.decl.constructorIndex == tester.constructorIndex).expr
    }

    // (_ is C) ((_ update-field s) v x) ==> (_ is C) x
    if (value is KAdtUpdateFieldApp<*>) return mkAdtTesterApp(tester, value.value)

    return mkAdtTesterAppNoSimplify(tester, value)
}

fun <T : KSort> KContext.simplifyAdtUpdateFieldApp(
    updater: KAdtUpdateFieldDecl<T>,
    value: KExpr<KAdtSort>,
    fieldValue: KExpr<T>
): KExpr<KAdtSort> {
    if (value is KAdtConstructorApp) {
        // ((_ update-field s_i v) (C' a1 .. an)) ==> (C' a1 .. an), C' is not the constructor of s_i
        if (value.decl.constructorIndex != updater.constructorIndex) return value

        // ((_ update-field s_i v) (C a1 .. an)) ==> (C a1 .. v .. an)
        val args = value.args.toMutableList().apply { set(updater.fieldIndex, fieldValue.uncheckedCast()) }
        return mkAdtConstructorApp(value.decl, args)
    }
    return mkAdtUpdateFieldAppNoSimplify(updater, value, fieldValue)
}
