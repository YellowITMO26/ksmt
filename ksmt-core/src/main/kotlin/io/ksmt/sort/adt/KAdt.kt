package io.ksmt.sort.adt

import io.ksmt.KContext
import io.ksmt.cache.hash
import io.ksmt.decl.KAdtCtorDecl
import io.ksmt.sort.KSort
import io.ksmt.sort.KSortVisitor
import io.ksmt.sort.substituteSorts

class KAdtSort internal constructor(
    ctx: KContext,
    val definition: KAdtDefinition,
    val args: List<KSort>
) : KSort(ctx) {
    init {
        require(args.size == definition.typeParameters.size) {
            "Datatype ${definition.name} expects ${definition.typeParameters.size} type arguments, " +
                    "but ${args.size} provided"
        }
    }

    val name = definition.name

    val constructors: List<KAdtCtorDecl> by lazy {
        definition.constructors.indices.map { ctx.mkAdtConstructorDecl(this, it) }
    }

    private val fieldSortsCache: List<List<KSort>> by lazy {
        val substitution = definition.typeParameters.zip(args).toMap()
        definition.constructors.map { ctor ->
            ctor.fields.map { ctx.substituteSorts(it.sort, substitution) }
        }
    }

    fun fieldSorts(constructorIndex: Int): List<KSort> = fieldSortsCache[constructorIndex]

    fun fieldSort(constructorIndex: Int, fieldIndex: Int): KSort =
        fieldSortsCache[constructorIndex][fieldIndex]

    override fun <T> accept(visitor: KSortVisitor<T>): T = visitor.visit(this)

    override fun print(builder: StringBuilder) = with(builder) {
        if (args.isEmpty()) {
            append(definition.name)
            return@with
        }

        append('(')
        append(definition.name)
        args.forEach {
            append(' ')
            it.print(this)
        }
        append(')')
    }

    override fun hashCode(): Int = hash(javaClass, System.identityHashCode(definition), args)

    override fun equals(other: Any?): Boolean =
        this === other || (other is KAdtSort && definition === other.definition && args == other.args)
}

class KAdtTypeParameterSort internal constructor(
    ctx: KContext,
    val name: String
) : KSort(ctx) {
    override fun <T> accept(visitor: KSortVisitor<T>): T = visitor.visit(this)

    override fun print(builder: StringBuilder) {
        builder.append(name)
    }

    override fun hashCode(): Int = hash(javaClass, name)

    override fun equals(other: Any?): Boolean =
        this === other || (other is KAdtTypeParameterSort && name == other.name)
}
