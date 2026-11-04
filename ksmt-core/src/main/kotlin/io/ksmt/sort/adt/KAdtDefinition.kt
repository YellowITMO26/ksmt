package io.ksmt.sort.adt

import io.ksmt.KContext
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArray3Sort
import io.ksmt.sort.KArrayNSort
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KFpRoundingModeSort
import io.ksmt.sort.KFpSort
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import io.ksmt.sort.KSort
import io.ksmt.sort.KSortVisitor
import io.ksmt.sort.KUninterpretedSort

class KAdtDefinition internal constructor(
    val ctx: KContext,
    val name: String,
    val typeParameters: List<KAdtTypeParameterSort>
) {
    private var definedConstructors: List<KAdtCtorDefinition>? = null
    private var definitionGroup: List<KAdtDefinition>? = null

    val constructors: List<KAdtCtorDefinition>
        get() = definedConstructors ?: error("Constructors of datatype $name are not defined yet")

    val group: List<KAdtDefinition>
        get() = definitionGroup ?: error("Datatype $name is not finalized yet")

    val isDefined: Boolean
        get() = definedConstructors != null

    val isParametric: Boolean
        get() = typeParameters.isNotEmpty()

    val selfSort: KAdtSort
        get() = ctx.mkAdtSort(this, typeParameters)

    fun sort(args: List<KSort>): KAdtSort = ctx.mkAdtSort(this, args)

    fun sort(vararg args: KSort): KAdtSort = ctx.mkAdtSort(this, args.toList())

    val wellFoundedConstructorIndex: Int by lazy {
        computeWellFoundedConstructors(group)[this]
            ?: error("Datatype $name is not well-founded")
    }

    internal fun defineConstructors(constructors: List<KAdtCtorDefinition>) {
        check(definedConstructors == null) { "Constructors of datatype $name are already defined" }
        require(constructors.isNotEmpty()) { "Datatype $name must have at least one constructor" }
        definedConstructors = constructors
    }

    internal fun finalize(group: List<KAdtDefinition>) {
        check(definitionGroup == null) { "Datatype $name is already finalized" }
        definitionGroup = group
    }

    override fun toString(): String = name

    companion object {
        private fun computeWellFoundedConstructors(group: List<KAdtDefinition>): Map<KAdtDefinition, Int> {
            val result = hashMapOf<KAdtDefinition, Int>()
            val checker = InhabitationChecker(group.toHashSet(), result)

            do {
                var changed = false
                for (definition in group) {
                    if (definition in result) continue
                    val idx = definition.constructors.indexOfFirst { ctor ->
                        ctor.fields.all { it.sort.accept(checker) }
                    }
                    if (idx >= 0) {
                        result[definition] = idx
                        changed = true
                    }
                }
            } while (changed)

            return result
        }
    }

    private class InhabitationChecker(
        private val groupDefinitions: Set<KAdtDefinition>,
        private val inhabited: Map<KAdtDefinition, Int>
    ) : KSortVisitor<Boolean> {
        override fun visit(sort: KBoolSort): Boolean = true

        override fun visit(sort: KIntSort): Boolean = true

        override fun visit(sort: KRealSort): Boolean = true

        override fun <S : KBvSort> visit(sort: S): Boolean = true

        override fun <S : KFpSort> visit(sort: S): Boolean = true

        override fun visit(sort: KFpRoundingModeSort): Boolean = true

        override fun visit(sort: KUninterpretedSort): Boolean = true

        override fun visit(sort: KAdtTypeParameterSort): Boolean = true

        override fun <D : KSort, R : KSort> visit(sort: KArraySort<D, R>): Boolean = sort.range.accept(this)

        override fun <D0 : KSort, D1 : KSort, R : KSort> visit(sort: KArray2Sort<D0, D1, R>): Boolean =
            sort.range.accept(this)

        override fun <D0 : KSort, D1 : KSort, D2 : KSort, R : KSort> visit(
            sort: KArray3Sort<D0, D1, D2, R>
        ): Boolean = sort.range.accept(this)

        override fun <R : KSort> visit(sort: KArrayNSort<R>): Boolean = sort.range.accept(this)

        override fun visit(sort: KAdtSort): Boolean {
            val definitionInhabited = sort.definition !in groupDefinitions || sort.definition in inhabited
            return definitionInhabited && sort.args.all { it.accept(this) }
        }
    }
}

class KAdtCtorFieldDefinition(
    val name: String,
    val sort: KSort
)

class KAdtCtorDefinition(
    val name: String,
    val fields: List<KAdtCtorFieldDefinition>
) {
    val testerName = "is-$name"
}

class KAdtDefinitionsBuilder internal constructor(val ctx: KContext) {
    private val definitions = mutableListOf<KAdtDefinition>()

    fun declare(name: String, typeParameters: List<String>): KAdtDefinition {
        val params = typeParameters.map { ctx.mkAdtTypeParameterSort(it) }
        return KAdtDefinition(ctx, name, params).also { definitions += it }
    }

    fun declare(name: String, vararg typeParameters: String): KAdtDefinition =
        declare(name, typeParameters.toList())

    fun define(definition: KAdtDefinition, constructors: List<KAdtCtorDefinition>) {
        require(definition in definitions) { "Datatype ${definition.name} is not declared in this group" }
        definition.defineConstructors(constructors)
    }

    fun KAdtDefinition.define(body: KAdtCtorsBuilder.() -> Unit) {
        val builder = KAdtCtorsBuilder(this)
        builder.body()
        define(this, builder.build())
    }

    fun build(): List<KAdtDefinition> {
        val group = definitions.toList()
        group.forEach {
            check(it.isDefined) { "Datatype ${it.name} was declared but not defined" }
        }
        group.forEach { it.finalize(group) }
        return group
    }
}

class KAdtCtorsBuilder internal constructor(val definition: KAdtDefinition) {
    private val constructors = mutableListOf<KAdtCtorDefinition>()

    val ctx: KContext
        get() = definition.ctx

    val selfSort: KAdtSort
        get() = definition.selfSort

    fun addConstructor(name: String, fields: List<KAdtCtorFieldDefinition>) {
        constructors += KAdtCtorDefinition(name, fields)
    }

    fun addConstructor(name: String, vararg fields: Pair<String, KSort>) =
        addConstructor(name, fields.map { (fieldName, sort) -> KAdtCtorFieldDefinition(fieldName, sort) })

    internal fun build(): List<KAdtCtorDefinition> = constructors.toList()
}
