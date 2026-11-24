package io.ksmt.solver.z3

import com.microsoft.z3.Native
import com.microsoft.z3.decRefUnsafe
import com.microsoft.z3.incRefUnsafe
import io.ksmt.solver.KSolverUnsupportedFeatureException
import io.ksmt.solver.util.KExprLongInternalizerBase.Companion.NOT_INTERNALIZED
import io.ksmt.sort.adt.KAdtCtorDefinition
import io.ksmt.sort.adt.KAdtDefinition
import io.ksmt.sort.adt.KAdtSort
import io.ksmt.sort.adt.KAdtTypeParameterSort
import io.ksmt.sort.KArray2Sort
import io.ksmt.sort.KArray3Sort
import io.ksmt.sort.KArrayNSort
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBvSort
import io.ksmt.sort.KFp128Sort
import io.ksmt.sort.KFp16Sort
import io.ksmt.sort.KFp32Sort
import io.ksmt.sort.KFp64Sort
import io.ksmt.sort.KFpCustomSizeSort
import io.ksmt.sort.KFpRoundingModeSort
import io.ksmt.sort.KFpSort
import io.ksmt.sort.KIntSort
import io.ksmt.sort.KRealSort
import io.ksmt.sort.KSort
import io.ksmt.sort.KSortVisitor
import io.ksmt.sort.KUninterpretedSort


open class KZ3SortInternalizer(
    private val z3Ctx: KZ3Context
) : KSortVisitor<Unit> {
    private var internalizedSort: Long = NOT_INTERNALIZED
    private val nCtx: Long = z3Ctx.nCtx
    private val datatypes: KZ3DatatypeRegistry = z3Ctx.datatypes

    override fun visit(sort: KBoolSort) {
        internalizedSort = Native.mkBoolSort(nCtx)
    }

    override fun visit(sort: KIntSort) {
        internalizedSort = Native.mkIntSort(nCtx)
    }

    override fun visit(sort: KRealSort) {
        internalizedSort = Native.mkRealSort(nCtx)
    }

    override fun <D : KSort, R : KSort> visit(sort: KArraySort<D, R>) {
        val domain = internalizeZ3Sort(sort.domain)
        val range = internalizeZ3Sort(sort.range)
        internalizedSort = Native.mkArraySort(nCtx, domain, range)
    }

    override fun <D0 : KSort, D1 : KSort, R : KSort> visit(sort: KArray2Sort<D0, D1, R>) {
        val domain = longArrayOf(
            internalizeZ3Sort(sort.domain0),
            internalizeZ3Sort(sort.domain1)
        )
        val range = internalizeZ3Sort(sort.range)
        internalizedSort = Native.mkArraySortN(nCtx, domain.size, domain, range)
    }

    override fun <D0 : KSort, D1 : KSort, D2 : KSort, R : KSort> visit(sort: KArray3Sort<D0, D1, D2, R>) {
        val domain = longArrayOf(
            internalizeZ3Sort(sort.domain0),
            internalizeZ3Sort(sort.domain1),
            internalizeZ3Sort(sort.domain2)
        )
        val range = internalizeZ3Sort(sort.range)
        internalizedSort = Native.mkArraySortN(nCtx, domain.size, domain, range)
    }

    override fun <R : KSort> visit(sort: KArrayNSort<R>) {
        val domain = sort.domainSorts.let { sorts ->
            LongArray(sorts.size) { internalizeZ3Sort(sorts[it]) }
        }
        val range = internalizeZ3Sort(sort.range)
        internalizedSort = Native.mkArraySortN(nCtx, domain.size, domain, range)
    }

    override fun visit(sort: KFpRoundingModeSort) {
        internalizedSort = Native.mkFpaRoundingModeSort(nCtx)
    }

    override fun <T : KBvSort> visit(sort: T) {
        val size = sort.sizeBits.toInt()
        internalizedSort = Native.mkBvSort(nCtx, size)
    }

    override fun <S : KFpSort> visit(sort: S) {
        internalizedSort = when (sort) {
            is KFp16Sort -> Native.mkFpaSort16(nCtx)
            is KFp32Sort -> Native.mkFpaSort32(nCtx)
            is KFp64Sort -> Native.mkFpaSort64(nCtx)
            is KFp128Sort -> Native.mkFpaSort128(nCtx)
            is KFpCustomSizeSort -> {
                val exp = sort.exponentBits.toInt()
                val significand = sort.significandBits.toInt()
                Native.mkFpaSort(nCtx, exp, significand)
            }

            else -> error("Unsupported sort: $sort")
        }
    }

    override fun visit(sort: KUninterpretedSort) {
        val sortName = Native.mkStringSymbol(nCtx, sort.name)
        internalizedSort = Native.mkUninterpretedSort(nCtx, sortName)
    }

    override fun visit(sort: KAdtTypeParameterSort) {
        internalizedSort = Native.mkTypeVariable(nCtx, Native.mkStringSymbol(nCtx, sort.name))
    }

    override fun visit(sort: KAdtSort) {
        val definition = sort.definition
        if (definition !in groupInProgress) {
            declareAdtGroup(definition)
        }
        internalizedSort = if (definition.isParametric || definition in groupInProgress) {
            checkForwardReferenceSupported(definition)
            val args = LongArray(sort.args.size) { internalizeZ3Sort(sort.args[it]) }
            val name = Native.mkStringSymbol(nCtx, datatypes.nativeName(definition))
            Native.mkDatatypeSort(nCtx, name, args.size, args)
        } else {
            datatypes.findDeclaredSort(definition)
        }
    }

    private var groupInProgress: Set<KAdtDefinition> = emptySet()
    private val groupLocalSorts = hashMapOf<KSort, Long>()

    private fun declareAdtGroup(definition: KAdtDefinition) {
        if (datatypes.findDeclaredSort(definition) != NOT_INTERNALIZED) {
            return
        }
        val previous = groupInProgress
        val previousLocalSorts = HashMap(groupLocalSorts)
        groupInProgress = definition.group.toSet()
        groupLocalSorts.clear()
        try {
            if (definition.group.any { it.isParametric }) {
                definition.group.forEach { declareAdt(it) }
            } else {
                declareMonomorphicAdtGroup(definition.group)
            }
        } finally {
            groupLocalSorts.values.forEach { decRefUnsafe(nCtx, it) }
            groupLocalSorts.clear()
            groupLocalSorts.putAll(previousLocalSorts)
            groupInProgress = previous
        }
    }

    private fun declareMonomorphicAdtGroup(group: List<KAdtDefinition>) {
        val groupSorts = group.map { it.selfSort }
        val constructors = group.map { definition ->
            definition.constructors.map { mkNativeConstructor(it) { sort -> groupSorts.indexOf(sort) } }.toLongArray()
        }
        val constructorLists = LongArray(group.size) {
            Native.mkConstructorList(nCtx, constructors[it].size, constructors[it])
        }
        try {
            val names = LongArray(group.size) { Native.mkStringSymbol(nCtx, datatypes.nativeName(group[it])) }
            val sorts = LongArray(group.size)
            Native.mkDatatypes(nCtx, group.size, names, sorts, constructorLists)
            group.forEachIndexed { idx, definition -> datatypes.saveDeclaredSort(definition, sorts[idx]) }
        } finally {
            constructorLists.forEach { Native.delConstructorList(nCtx, it) }
            constructors.forEach { ctors -> ctors.forEach { Native.delConstructor(nCtx, it) } }
        }
    }

    private var declaringDefinition: KAdtDefinition? = null

    private fun declareAdt(definition: KAdtDefinition) {
        val selfSort = definition.selfSort
        val previousDeclaring = declaringDefinition
        declaringDefinition = definition
        val constructors = try {
            definition.constructors.map { ctor ->
                mkNativeConstructor(ctor) { sort ->
                    if (sort == selfSort) {
                        0
                    } else {
                        -1
                    }
                }
            }.toLongArray()
        } finally {
            declaringDefinition = previousDeclaring
        }
        try {
            val params = LongArray(definition.typeParameters.size) {
                internalizeZ3Sort(definition.typeParameters[it])
            }
            val sort = Native.mkPolymorphicDatatype(
                nCtx,
                Native.mkStringSymbol(nCtx, datatypes.nativeName(definition)),
                params.size, params,
                constructors.size, constructors
            )
            datatypes.saveDeclaredSort(definition, sort)
        } finally {
            constructors.forEach { Native.delConstructor(nCtx, it) }
        }
    }

    private fun mkNativeConstructor(ctor: KAdtCtorDefinition, groupIndex: (KSort) -> Int): Long {
        val fieldNames = LongArray(ctor.fields.size) { Native.mkStringSymbol(nCtx, ctor.fields[it].name) }
        val refs = IntArray(ctor.fields.size) { groupIndex(ctor.fields[it].sort) }
        val fieldSorts = LongArray(ctor.fields.size) {
            if (refs[it] >= 0) {
                0L
            } else {
                internalizeZ3Sort(ctor.fields[it].sort)
            }
        }
        return Native.mkConstructor(
            nCtx,
            Native.mkStringSymbol(nCtx, ctor.name),
            Native.mkStringSymbol(nCtx, ctor.testerName),
            ctor.fields.size,
            fieldNames,
            fieldSorts,
            IntArray(ctor.fields.size) { maxOf(refs[it], 0) }
        )
    }

    fun internalizeZ3Sort(sort: KSort): Long {
        if (groupInProgress.isNotEmpty()) {
            groupLocalSorts[sort]?.let { return it }
            sort.accept(this)
            val native = internalizedSort
            incRefUnsafe(nCtx, native)
            groupLocalSorts[sort] = native
            return native
        }
        return z3Ctx.internalizeSort(sort) {
            sort.accept(this@KZ3SortInternalizer)
            internalizedSort
        }
    }

    private fun checkForwardReferenceSupported(definition: KAdtDefinition) {
        val declaring = declaringDefinition
        if (definition in groupInProgress && definition.isParametric && declaring != null && !declaring.isParametric) {
            throw KSolverUnsupportedFeatureException(
                "Datatype ${declaring.name} references parametric datatype ${definition.name} " +
                    "from the same mutually recursive group: this is not supported in Z3"
            )
        }
    }
}
