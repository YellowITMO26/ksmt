package io.ksmt.expr

import io.ksmt.KContext
import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.decl.KAdtCtorDecl
import io.ksmt.decl.KAdtSelectorDecl
import io.ksmt.decl.KAdtTesterDecl
import io.ksmt.decl.KAdtUpdateFieldDecl
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.adt.KAdtSort
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KSort
import io.ksmt.utils.uncheckedCast

class KAdtConstructorApp internal constructor(
    ctx: KContext,
    override val decl: KAdtCtorDecl,
    override val args: List<KExpr<KSort>>
) : KApp<KAdtSort, KSort>(ctx) {
    override val sort: KAdtSort
        get() = decl.adtSort

    override fun accept(transformer: KTransformerBase): KExpr<KAdtSort> = transformer.transform(this)

    override fun print(printer: ExpressionPrinter) {
        if (args.isNotEmpty() || sort.args.isEmpty()) {
            super.print(printer)
            return
        }

        with(printer) {
            append("(as ")
            append(decl.name)
            append(" ")
            append(sort.toString())
            append(")")
        }
    }

    override fun internHashCode(): Int = hash(decl, args)
    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { decl }, { args })
}

class KAdtSelectorApp<T : KSort> internal constructor(
    ctx: KContext,
    override val decl: KAdtSelectorDecl<T>,
    val value: KExpr<KAdtSort>
) : KApp<T, KAdtSort>(ctx) {
    override val sort: T
        get() = decl.sort

    override val args: List<KExpr<KAdtSort>>
        get() = listOf(value)

    override fun accept(transformer: KTransformerBase): KExpr<T> = transformer.transform(this)

    override fun internHashCode(): Int = hash(decl, value)
    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { decl }, { value })
}

class KAdtTesterApp internal constructor(
    ctx: KContext,
    override val decl: KAdtTesterDecl,
    val value: KExpr<KAdtSort>
) : KApp<KBoolSort, KAdtSort>(ctx) {
    override val sort: KBoolSort
        get() = ctx.boolSort

    override val args: List<KExpr<KAdtSort>>
        get() = listOf(value)

    override fun accept(transformer: KTransformerBase): KExpr<KBoolSort> = transformer.transform(this)

    override fun internHashCode(): Int = hash(decl, value)
    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { decl }, { value })
}

class KAdtUpdateFieldApp<T : KSort> internal constructor(
    ctx: KContext,
    override val decl: KAdtUpdateFieldDecl<T>,
    val value: KExpr<KAdtSort>,
    val fieldValue: KExpr<T>
) : KApp<KAdtSort, KSort>(ctx) {
    override val sort: KAdtSort
        get() = decl.adtSort

    override val args: List<KExpr<KSort>>
        get() = listOf<KExpr<*>>(value, fieldValue).uncheckedCast()

    override fun accept(transformer: KTransformerBase): KExpr<KAdtSort> = transformer.transform(this)

    override fun print(printer: ExpressionPrinter) {
        with(printer) {
            append("((_ update-field ")
            append(decl.selector.name)
            append(") ")
            append(value)
            append(" ")
            append(fieldValue)
            append(")")
        }
    }

    override fun internHashCode(): Int = hash(decl, value, fieldValue)
    override fun internEquals(other: Any): Boolean =
        structurallyEqual(other, { decl }, { value }, { fieldValue })
}
