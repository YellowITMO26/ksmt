package io.ksmt.expr.rewrite.simplify

import io.ksmt.KContext
import io.ksmt.decl.KAdtCtorDecl
import io.ksmt.decl.KAdtSelectorDecl
import io.ksmt.decl.KAdtTesterDecl
import io.ksmt.decl.KAdtUpdateFieldDecl
import io.ksmt.expr.KExpr
import io.ksmt.sort.adt.KAdtSort
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KSort

fun KContext.simplifyAdtConstructorApp(
    constructor: KAdtCtorDecl,
    args: List<KExpr<*>>
): KExpr<KAdtSort> = mkAdtConstructorAppNoSimplify(constructor, args)

fun <T : KSort> KContext.simplifyAdtSelectorApp(
    selector: KAdtSelectorDecl<T>,
    value: KExpr<KAdtSort>
): KExpr<T> = mkAdtSelectorAppNoSimplify(selector, value)

fun KContext.simplifyAdtTesterApp(
    tester: KAdtTesterDecl,
    value: KExpr<KAdtSort>
): KExpr<KBoolSort> = mkAdtTesterAppNoSimplify(tester, value)

fun <T : KSort> KContext.simplifyAdtUpdateFieldApp(
    updater: KAdtUpdateFieldDecl<T>,
    value: KExpr<KAdtSort>,
    fieldValue: KExpr<T>
): KExpr<KAdtSort> = mkAdtUpdateFieldAppNoSimplify(updater, value, fieldValue)
