package io.ksmt.decl

import io.ksmt.KContext
import io.ksmt.expr.KApp
import io.ksmt.expr.KExpr
import io.ksmt.sort.adt.KAdtCtorDefinition
import io.ksmt.sort.adt.KAdtCtorFieldDefinition
import io.ksmt.sort.adt.KAdtSort
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KSort
import io.ksmt.utils.uncheckedCast

class KAdtCtorDecl(
    ctx: KContext,
    val adtSort: KAdtSort,
    val constructorIndex: Int
) : KFuncDecl<KAdtSort>(
    ctx,
    name = adtSort.definition.constructors[constructorIndex].name,
    resultSort = adtSort,
    argSorts = adtSort.fieldSorts(constructorIndex)
) {
    val definition: KAdtCtorDefinition
        get() = adtSort.definition.constructors[constructorIndex]

    val fields: List<KAdtCtorFieldDefinition>
        get() = definition.fields

    val selectors: List<KAdtSelectorDecl<KSort>>
        get() = fields.indices.map { ctx.mkAdtSelectorDecl(adtSort, constructorIndex, it) }

    val tester: KAdtTesterDecl
        get() = ctx.mkAdtTesterDecl(adtSort, constructorIndex)

    val updaters: List<KAdtUpdateFieldDecl<KSort>>
        get() = fields.indices.map { ctx.mkAdtUpdateFieldDecl(adtSort, constructorIndex, it) }

    override fun apply(args: List<KExpr<*>>): KApp<KAdtSort, *> {
        checkArgSorts(args)
        return ctx.mkAdtConstructorAppNoSimplify(this, args)
    }

    override fun <R> accept(visitor: KDeclVisitor<R>): R = visitor.visit(this)
}

class KAdtSelectorDecl<T : KSort>(
    ctx: KContext,
    val adtSort: KAdtSort,
    val constructorIndex: Int,
    val fieldIndex: Int
) : KFuncDecl1<T, KAdtSort>(
    ctx,
    name = adtSort.definition.constructors[constructorIndex].fields[fieldIndex].name,
    resultSort = adtSort.fieldSort(constructorIndex, fieldIndex).uncheckedCast(),
    argSort = adtSort
) {
    val constructor: KAdtCtorDecl
        get() = ctx.mkAdtConstructorDecl(adtSort, constructorIndex)

    override fun KContext.apply(arg: KExpr<KAdtSort>): KApp<T, KAdtSort> =
        mkAdtSelectorAppNoSimplify(this@KAdtSelectorDecl, arg)

    override fun <R> accept(visitor: KDeclVisitor<R>): R = visitor.visit(this)
}

class KAdtTesterDecl(
    ctx: KContext,
    val adtSort: KAdtSort,
    val constructorIndex: Int
) : KFuncDecl1<KBoolSort, KAdtSort>(
    ctx,
    name = adtSort.definition.constructors[constructorIndex].testerName,
    resultSort = ctx.boolSort,
    argSort = adtSort
) {
    val constructor: KAdtCtorDecl
        get() = ctx.mkAdtConstructorDecl(adtSort, constructorIndex)

    override fun KContext.apply(arg: KExpr<KAdtSort>): KApp<KBoolSort, KAdtSort> =
        mkAdtTesterAppNoSimplify(this@KAdtTesterDecl, arg)

    override fun <R> accept(visitor: KDeclVisitor<R>): R = visitor.visit(this)
}

class KAdtUpdateFieldDecl<T : KSort>(
    ctx: KContext,
    val adtSort: KAdtSort,
    val constructorIndex: Int,
    val fieldIndex: Int
) : KFuncDecl2<KAdtSort, KAdtSort, T>(
    ctx,
    name = "update-${adtSort.definition.constructors[constructorIndex].fields[fieldIndex].name}",
    resultSort = adtSort,
    arg0Sort = adtSort,
    arg1Sort = adtSort.fieldSort(constructorIndex, fieldIndex).uncheckedCast()
) {
    val selector: KAdtSelectorDecl<T>
        get() = ctx.mkAdtSelectorDecl(adtSort, constructorIndex, fieldIndex)

    override fun KContext.apply(arg0: KExpr<KAdtSort>, arg1: KExpr<T>): KApp<KAdtSort, *> =
        mkAdtUpdateFieldAppNoSimplify(this@KAdtUpdateFieldDecl, arg0, arg1)

    override fun <R> accept(visitor: KDeclVisitor<R>): R = visitor.visit(this)
}
