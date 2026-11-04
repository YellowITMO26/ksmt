package io.ksmt.solver.z3

import com.microsoft.z3.Context
import com.microsoft.z3.incRefUnsafe
import io.ksmt.solver.util.KExprLongInternalizerBase.Companion.NOT_INTERNALIZED
import io.ksmt.sort.adt.KAdtDefinition
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import java.util.IdentityHashMap

class KZ3DatatypeRegistry(nativeContext: Context) {
    private val nCtx: Long = nativeContext.nCtx()

    private val nativeNames = IdentityHashMap<KAdtDefinition, String>()
    private val usedNativeNames = hashSetOf<String>()
    private val declaredSorts = IdentityHashMap<KAdtDefinition, Long>()
    private val definitionsByNativeSort = Long2ObjectOpenHashMap<KAdtDefinition>()
    private val parametricDefinitionsByName = hashMapOf<String, KAdtDefinition>()

    fun nativeName(definition: KAdtDefinition): String = nativeNames.getOrPut(definition) {
        var name = definition.name
        var idx = 0
        while (!usedNativeNames.add(name)) {
            name = "${definition.name}!${idx++}"
        }
        name
    }

    fun registerNativeName(definition: KAdtDefinition, name: String) {
        if (nativeNames.putIfAbsent(definition, name) == null) {
            usedNativeNames.add(name)
        }
    }

    fun findDeclaredSort(definition: KAdtDefinition): Long =
        declaredSorts[definition] ?: NOT_INTERNALIZED

    fun saveDeclaredSort(definition: KAdtDefinition, sort: Long) {
        if (declaredSorts.putIfAbsent(definition, sort) != null) {
            return
        }
        incRefUnsafe(nCtx, sort)

        if (definition.isParametric) {
            parametricDefinitionsByName.putIfAbsent(nativeName(definition), definition)
        } else {
            definitionsByNativeSort.putIfAbsent(sort, definition)
        }
    }

    fun findDefinitionByNativeSort(sort: Long): KAdtDefinition? = definitionsByNativeSort.get(sort)

    fun findParametricDefinitionByName(name: String): KAdtDefinition? = parametricDefinitionsByName[name]
}
