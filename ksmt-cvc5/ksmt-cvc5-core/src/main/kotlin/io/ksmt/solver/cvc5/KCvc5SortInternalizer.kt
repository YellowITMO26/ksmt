package io.ksmt.solver.cvc5

import io.github.cvc5.DatatypeDecl
import io.github.cvc5.Sort
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
import io.ksmt.sort.adt.KAdtCtorDefinition
import io.ksmt.sort.adt.KAdtDefinition
import io.ksmt.sort.adt.KAdtSort
import io.ksmt.sort.adt.KAdtTypeParameterSort

open class KCvc5SortInternalizer(
    private val cvc5Ctx: KCvc5Context
) : KSortVisitor<Sort> {
    private val tm: KCvc5TermManager = cvc5Ctx.termManager
    private val datatypes: KCvc5DatatypeRegistry = tm.datatypes

    private var groupInProgress: Set<KAdtDefinition> = emptySet()
    private val groupLocalSorts = hashMapOf<KSort, Sort>()

    override fun visit(sort: KBoolSort): Sort = internalizeSort(sort) {
        tm.builder { booleanSort }
    }

    override fun visit(sort: KIntSort): Sort = internalizeSort(sort) {
        tm.builder { integerSort }
    }

    override fun visit(sort: KRealSort): Sort = internalizeSort(sort) {
        tm.builder { realSort }
    }

    override fun <D : KSort, R : KSort> visit(sort: KArraySort<D, R>): Sort =
        internalizeSort(sort) {
            val domain = sort.domain.internalizeCvc5Sort()
            val range = sort.range.internalizeCvc5Sort()
            tm.builder { mkArraySort(domain, range) }
        }

    override fun <D0 : KSort, D1 : KSort, R : KSort> visit(sort: KArray2Sort<D0, D1, R>): Sort =
        internalizeSort(sort) {
            val d0 = sort.domain0.internalizeCvc5Sort()
            val d1 = sort.domain1.internalizeCvc5Sort()

            val domain = tm.builder { mkTupleSort(arrayOf(d0, d1)) }
            val range = sort.range.internalizeCvc5Sort()
            tm.builder { mkArraySort(domain, range) }
        }

    override fun <D0 : KSort, D1 : KSort, D2 : KSort, R : KSort> visit(sort: KArray3Sort<D0, D1, D2, R>): Sort =
        internalizeSort(sort) {
            val d0 = sort.domain0.internalizeCvc5Sort()
            val d1 = sort.domain1.internalizeCvc5Sort()
            val d2 = sort.domain2.internalizeCvc5Sort()

            val domain = tm.builder { mkTupleSort(arrayOf(d0, d1, d2)) }
            val range = sort.range.internalizeCvc5Sort()
            tm.builder { mkArraySort(domain, range) }
        }

    override fun <R : KSort> visit(sort: KArrayNSort<R>): Sort =
        internalizeSort(sort) {
            val domainSorts = sort.domainSorts.map { it.internalizeCvc5Sort() }
            val domain = tm.builder { mkTupleSort(domainSorts.toTypedArray()) }
            val range = sort.range.internalizeCvc5Sort()
            tm.builder { mkArraySort(domain, range) }
        }

    override fun visit(sort: KFpRoundingModeSort): Sort = internalizeSort(sort) {
        tm.builder { roundingModeSort }
    }

    override fun <T : KBvSort> visit(sort: T): Sort = internalizeSort(sort) {
        val size = sort.sizeBits.toInt()
        tm.builder { mkBitVectorSort(size) }
    }

    override fun <S : KFpSort> visit(sort: S): Sort = internalizeSort(sort) {
        val exp = sort.exponentBits.toInt()
        val significand = sort.significandBits.toInt()
        tm.builder { mkFloatingPointSort(exp, significand) }
    }

    override fun visit(sort: KUninterpretedSort): Sort = internalizeSort(sort) {
        // uninterpreted sorts incremental collection optimization
        cvc5Ctx.addUninterpretedSort(sort)
        tm.builder { mkUninterpretedSort(sort.name) }
    }

    override fun visit(sort: KAdtTypeParameterSort): Sort = internalizeSort(sort) {
        datatypes.typeParameter(sort.name)
    }

    override fun visit(sort: KAdtSort): Sort = internalizeSort(sort) {
        val definition = sort.definition
        val args = sort.args.map { it.internalizeCvc5Sort() }.toTypedArray()

        val template = if (definition in groupInProgress) {
            tm.builder { mkUnresolvedDatatypeSort(datatypes.nativeName(definition), args.size) }
        } else {
            declareAdtGroup(definition)
            datatypes.findDeclaredSort(definition) ?: error("Datatype ${definition.name} is not declared")
        }

        if (args.isEmpty()) {
            template
        } else {
            tm.sortOp(template) { instantiate(args) }
        }
    }

    private fun declareAdtGroup(definition: KAdtDefinition) {
        if (datatypes.findDeclaredSort(definition) != null) {
            return
        }

        val group = definition.group
        val previousGroup = groupInProgress
        val previousLocalSorts = HashMap(groupLocalSorts)

        groupInProgress = group.toSet()
        groupLocalSorts.clear()

        try {
            val declarations = group.map { mkDatatypeDecl(it) }.toTypedArray()
            val sorts = tm.termManager.mkDatatypeSorts(declarations)
            group.forEachIndexed { idx, def ->
                datatypes.saveDeclaredSort(def, tm.registerPointer(sorts[idx]))
            }
        } finally {
            groupLocalSorts.clear()
            groupLocalSorts.putAll(previousLocalSorts)
            groupInProgress = previousGroup
        }
    }

    private fun mkDatatypeDecl(definition: KAdtDefinition): DatatypeDecl {
        val params = definition.typeParameters.map { it.internalizeCvc5Sort() }.toTypedArray()
        val name = datatypes.nativeName(definition)
        val decl = if (params.isEmpty()) {
            tm.builder { mkDatatypeDecl(name) }
        } else {
            tm.builder { mkDatatypeDecl(name, params) }
        }
        definition.constructors.forEach { decl.addConstructor(mkConstructorDecl(it)) }
        return decl
    }

    private fun mkConstructorDecl(ctor: KAdtCtorDefinition) =
        tm.builder { mkDatatypeConstructorDecl(ctor.name) }.also { decl ->
            ctor.fields.forEach { decl.addSelector(it.name, it.sort.internalizeCvc5Sort()) }
        }

    private inline fun internalizeSort(sort: KSort, internalizer: () -> Sort): Sort =
        if (groupInProgress.isEmpty()) {
            cvc5Ctx.internalizeSort(sort, internalizer)
        } else {
            groupLocalSorts.getOrPut(sort, internalizer)
        }

    @Suppress("MemberVisibilityCanBePrivate")
    fun <T : KSort> T.internalizeCvc5Sort() = accept(this@KCvc5SortInternalizer)
}
