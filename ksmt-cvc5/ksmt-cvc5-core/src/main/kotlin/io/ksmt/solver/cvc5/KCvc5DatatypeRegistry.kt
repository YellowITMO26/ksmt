package io.ksmt.solver.cvc5

import io.github.cvc5.Datatype
import io.github.cvc5.Sort
import io.github.cvc5.Term
import io.ksmt.solver.KSolverUnsupportedFeatureException
import io.ksmt.sort.adt.KAdtDefinition
import java.util.IdentityHashMap

class KCvc5DatatypeRegistry internal constructor(private val tm: KCvc5TermManager) {
    private val declaredSorts = IdentityHashMap<KAdtDefinition, Sort>()
    private val convertedDefinitions = IdentityHashMap<KAdtDefinition, Datatype>()
    private val definitions = KCvc5TermMap<KAdtDefinition>()
    private val typeParameters = hashMapOf<String, Sort>()

    fun typeParameter(name: String): Sort = typeParameters.getOrPut(name) {
        tm.builder { mkParamSort(name) }
    }

    fun findDeclaredSort(definition: KAdtDefinition): Sort? {
        declaredSorts[definition]?.let { return it }
        if (definition in convertedDefinitions) {
            throw KSolverUnsupportedFeatureException(
                "Parametric datatype ${definition.name} declared outside of ksmt can't be internalized"
            )
        }
        return null
    }

    fun saveDeclaredSort(definition: KAdtDefinition, sort: Sort) {
        if (declaredSorts.putIfAbsent(definition, sort) != null) {
            return
        }
        definitions.putIfAbsent(datatypeKey(tm.sortOp(sort) { datatype }), definition)
    }

    fun saveConvertedDefinition(definition: KAdtDefinition, datatype: Datatype) {
        if (datatype.isParametric) {
            convertedDefinitions[definition] = datatype
            definitions.putIfAbsent(datatypeKey(datatype), definition)
            return
        }
        val ctor = tm.registerPointer(datatype.getConstructor(0))
        val ctorSort = tm.termSort(tm.registerPointer(ctor.term))
        saveDeclaredSort(definition, tm.sortOp(ctorSort) { datatypeConstructorCodomainSort })
    }

    fun findDefinition(datatype: Datatype): KAdtDefinition? = definitions[datatypeKey(datatype)]

    fun datatypeKey(datatype: Datatype): Term =
        tm.registerPointer(tm.registerPointer(datatype.getConstructor(0)).term)
}
