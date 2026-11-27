package io.ksmt

import io.ksmt.decl.KAdtSelectorDecl
import io.ksmt.decl.KAdtUpdateFieldDecl
import io.ksmt.expr.KAdtUpdateFieldApp
import io.ksmt.expr.rewrite.simplify.KExprSimplifier
import io.ksmt.sort.KIntSort
import io.ksmt.sort.adt.KAdtSort
import io.ksmt.utils.mkConst
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Execution(ExecutionMode.CONCURRENT)
class AdtSimplifyTest : ExpressionSimplifyTest() {

    @Test
    fun testSelectorOverConstructor() = runTest({ mkListSort() }) { list, runner ->
        val nil = mkAdtConstructorDecl(list, 0)
        val cons = mkAdtConstructorDecl(list, 1)
        val head = mkAdtSelectorDecl<KIntSort>(list, 1, 0)
        val tail = mkAdtSelectorDecl<KAdtSort>(list, 1, 1)

        val x = intSort.mkConst("x")
        val l = list.mkConst("l")
        val consApp = mkAdtConstructorApp(cons, listOf(x, l))
        val nilApp = mkAdtConstructorApp(nil, emptyList())

        assertEquals(x, mkAdtSelectorApp(head, consApp))
        assertEquals(l, mkAdtSelectorApp(tail, consApp))
        runner.check(mkAdtSelectorAppNoSimplify(head, consApp), mkAdtSelectorApp(head, consApp)) { "$consApp" }

        assertEquals(mkAdtSelectorAppNoSimplify(head, nilApp), mkAdtSelectorApp(head, nilApp))
        assertEquals(mkAdtSelectorAppNoSimplify(head, l), mkAdtSelectorApp(head, l))
    }

    @Test
    fun testSelectorOverUpdateField() = runTest({ mkBoxSort() }) { box, runner ->
        testSelectorOverUpdateField(box, runner, singleConstructor = true)
        testSelectorOverUpdateField(mkPairSort(), runner, singleConstructor = true)
        testSelectorOverUpdateField(mkListSort(), runner, singleConstructor = false)
    }

    private fun KContext.testSelectorOverUpdateField(
        sort: KAdtSort,
        runner: ExpressionSimplifyTest.TestRunner,
        singleConstructor: Boolean
    ) {
        val ctorIndex = sort.definition.constructors.indexOfFirst { it.fields.isNotEmpty() }
        val selector: KAdtSelectorDecl<KIntSort> = mkAdtSelectorDecl(sort, ctorIndex, 0)
        val updater: KAdtUpdateFieldDecl<KIntSort> = mkAdtUpdateFieldDecl(sort, ctorIndex, 0)
        val x = sort.mkConst("x")
        val v = mkConst("v", intSort)

        val update = mkAdtUpdateFieldAppNoSimplify(updater, x, v)

        val expected = if (singleConstructor) v else mkAdtSelectorAppNoSimplify(selector, update)
        assertEquals(expected, mkAdtSelectorApp(selector, update))
        runner.check(mkAdtSelectorAppNoSimplify(selector, update), expected) { "$update | $selector" }
    }

    @Test
    fun testTester() = runTest({ mkListSort() }) { list, _ ->
        val nil = mkAdtConstructorDecl(list, 0)
        val cons = mkAdtConstructorDecl(list, 1)
        val x = intSort.mkConst("x")
        val l = list.mkConst("l")
        val consApp = mkAdtConstructorApp(cons, listOf(x, l))
        val nilApp = mkAdtConstructorApp(nil, emptyList())

        assertEquals(trueExpr, mkAdtTesterApp(cons.tester, consApp))
        assertEquals(falseExpr, mkAdtTesterApp(cons.tester, nilApp))

        assertEquals(mkAdtTesterAppNoSimplify(cons.tester, l), mkAdtTesterApp(cons.tester, l))

        val update = mkAdtUpdateFieldAppNoSimplify(mkAdtUpdateFieldDecl<KIntSort>(list, 1, 0), l, x)
        assertEquals(mkAdtTesterAppNoSimplify(cons.tester, l), mkAdtTesterApp(cons.tester, update))

        val box = mkBoxSort()
        assertEquals(trueExpr, mkAdtTesterApp(mkAdtConstructorDecl(box, 0).tester, box.mkConst("b")))
    }

    @Test
    fun testUpdateFieldOverConstructor() = runTest({ mkListSort() }) { list, runner ->
        val nil = mkAdtConstructorDecl(list, 0)
        val cons = mkAdtConstructorDecl(list, 1)
        val x = intSort.mkConst("x")
        val l = list.mkConst("l")
        val consApp = mkAdtConstructorApp(cons, listOf(x, l))
        val nilApp = mkAdtConstructorApp(nil, emptyList())

        val expected = mkAdtConstructorAppNoSimplify(cons, listOf(5.expr, l))
        assertEquals(expected, mkAdtUpdateFieldApp(mkAdtUpdateFieldDecl<KIntSort>(list, 1, 0), consApp, 5.expr))
        runner.check(
            mkAdtUpdateFieldAppNoSimplify(mkAdtUpdateFieldDecl<KIntSort>(list, 1, 0), consApp, 5.expr), expected
        ) { "$consApp" }

        assertEquals(nilApp, mkAdtUpdateFieldApp(mkAdtUpdateFieldDecl<KIntSort>(list, 1, 0), nilApp, 5.expr))

        val update = mkAdtUpdateFieldApp(mkAdtUpdateFieldDecl<KIntSort>(list, 1, 0), l, 5.expr)
        assertIs<KAdtUpdateFieldApp<KIntSort>>(update)
        assertEquals(l, update.value)
    }

    @Test
    fun testConstructorEta() = runTest({ mkPairSort() }) { pair, runner ->
        val ctor = mkAdtConstructorDecl(pair, 0)
        val fst = ctor.selectors[0]
        val snd = ctor.selectors[1]
        val p = pair.mkConst("p")
        val q = pair.mkConst("q")

        assertEquals(p, mkAdtConstructorApp(ctor, listOf(mkAdtSelectorApp(fst, p), mkAdtSelectorApp(snd, p))))
        runner.check(
            mkAdtConstructorAppNoSimplify(ctor, listOf(mkAdtSelectorApp(fst, p), mkAdtSelectorApp(snd, p))), p
        ) { "$p" }

        assertEquals(
            mkAdtConstructorAppNoSimplify(ctor, listOf(mkAdtSelectorApp(fst, p), mkAdtSelectorApp(snd, q))),
            mkAdtConstructorApp(ctor, listOf(mkAdtSelectorApp(fst, p), mkAdtSelectorApp(snd, q)))
        )
        assertEquals(
            mkAdtConstructorAppNoSimplify(ctor, listOf(mkAdtSelectorApp(snd, p), mkAdtSelectorApp(fst, p))),
            mkAdtConstructorApp(ctor, listOf(mkAdtSelectorApp(snd, p), mkAdtSelectorApp(fst, p)))
        )
        assertEquals(
            mkAdtConstructorAppNoSimplify(ctor, listOf(mkAdtSelectorApp(fst, p), 1.expr)),
            mkAdtConstructorApp(ctor, listOf(mkAdtSelectorApp(fst, p), 1.expr))
        )
        val list = mkListSort()
        val cons = mkAdtConstructorDecl(list, 1)
        val l = list.mkConst("l")
        assertEquals(
            mkAdtConstructorAppNoSimplify(
                cons,
                listOf(mkAdtSelectorApp(cons.selectors[0], l), mkAdtSelectorApp(cons.selectors[1], l))
            ),
            mkAdtConstructorApp(
                cons,
                listOf(mkAdtSelectorApp(cons.selectors[0], l), mkAdtSelectorApp(cons.selectors[1], l))
            )
        )
    }

    @Test
    fun testEq() = runTest({ mkColorSort() }) { color, runner ->
        val simplifier = KExprSimplifier(this)
        val red = mkAdtConstructorApp(mkAdtConstructorDecl(color, 0), emptyList())
        val green = mkAdtConstructorApp(mkAdtConstructorDecl(color, 1), emptyList())

        assertEquals(falseExpr, simplifier.apply(mkEqNoSimplify(red, green)))
        assertEquals(trueExpr, simplifier.apply(mkEqNoSimplify(red, red)))
        runner.check(mkEqNoSimplify(red, green), falseExpr) { "red = green" }

        val box = mkBoxSort()
        val boxCtor = mkAdtConstructorDecl(box, 0)
        val x = intSort.mkConst("x")
        val y = intSort.mkConst("y")
        assertEquals(
            mkEq(x, y),
            simplifier.apply(
                mkEqNoSimplify(
                    mkAdtConstructorApp(boxCtor, listOf(x)),
                    mkAdtConstructorApp(boxCtor, listOf(y))
                )
            )
        )
        assertEquals(
            trueExpr,
            simplifier.apply(
                mkEqNoSimplify(
                    mkAdtConstructorApp(boxCtor, listOf(x)),
                    mkAdtConstructorApp(boxCtor, listOf(x))
                )
            )
        )

        val pair = mkPairSort()
        val pairCtor = mkAdtConstructorDecl(pair, 0)
        val a = intSort.mkConst("a")
        val b = intSort.mkConst("b")
        val pairEq = simplifier.apply(
            mkEqNoSimplify(mkAdtConstructorApp(pairCtor, listOf(x, y)), mkAdtConstructorApp(pairCtor, listOf(a, b)))
        )
        assertEquals(mkAnd(mkEq(x, a), mkEq(y, b)), pairEq)
        runner.check(
            mkEqNoSimplify(mkAdtConstructorApp(pairCtor, listOf(x, y)), mkAdtConstructorApp(pairCtor, listOf(a, b))),
            pairEq
        ) { "$pairEq" }

        val tree = mkTreeSort()
        val leaf = mkAdtConstructorApp(mkAdtConstructorDecl(tree, 0), emptyList())
        val node = mkAdtConstructorDecl(tree, 1)
        val nodeEq = simplifier.apply(
            mkEqNoSimplify(
                mkAdtConstructorApp(node, listOf(leaf, x, leaf)),
                mkAdtConstructorApp(node, listOf(leaf, y, leaf))
            )
        )
        assertEquals(mkEq(x, y), nodeEq)
    }

    @Test
    fun testEqConstructorClash() = runTest({ mkTreeSort() }) { tree, _ ->
        val simplifier = KExprSimplifier(this)
        val leaf = mkAdtConstructorApp(mkAdtConstructorDecl(tree, 0), emptyList())
        val node = mkAdtConstructorDecl(tree, 1)
        val x = intSort.mkConst("x")
        val y = mkConst("y", intSort)
        val t = tree.mkConst("t")

        assertEquals(
            falseExpr,
            simplifier.apply(mkEqNoSimplify(mkAdtConstructorApp(node, listOf(leaf, x, leaf)), leaf))
        )
        assertEquals(
            falseExpr,
            simplifier.apply(
                mkEqNoSimplify(
                    mkAdtConstructorApp(node, listOf(mkAdtConstructorApp(node, listOf(leaf, x, leaf)), y, t)),
                    mkAdtConstructorApp(node, listOf(leaf, y, t))
                )
            )
        )
        assertEquals(
            mkEq(x, y),
            simplifier.apply(
                mkEqNoSimplify(
                    mkAdtConstructorApp(node, listOf(t, x, leaf)),
                    mkAdtConstructorApp(node, listOf(t, y, leaf))
                )
            )
        )
        assertEquals(mkEq(t, leaf), simplifier.apply(mkEqNoSimplify(t, leaf)))
    }

    @Test
    fun testDistinct() = runTest({ mkColorSort() }) { color, runner ->
        val simplifier = KExprSimplifier(this)
        val red = mkAdtConstructorApp(mkAdtConstructorDecl(color, 0), emptyList())
        val green = mkAdtConstructorApp(mkAdtConstructorDecl(color, 1), emptyList())

        assertEquals(trueExpr, simplifier.apply(mkDistinctNoSimplify(listOf(red, green))))
        runner.check(mkDistinctNoSimplify(listOf(red, green)), trueExpr) { "red green" }

        val tree = mkTreeSort()
        val leaf = mkAdtConstructorApp(mkAdtConstructorDecl(tree, 0), emptyList())
        val node = mkAdtConstructorDecl(tree, 1)
        val x = intSort.mkConst("x")
        val t = tree.mkConst("t")
        assertEquals(
            trueExpr,
            simplifier.apply(mkDistinctNoSimplify(listOf(leaf, mkAdtConstructorApp(node, listOf(t, x, leaf)))))
        )

        val box = mkBoxSort()
        val boxCtor = mkAdtConstructorDecl(box, 0)
        val y = intSort.mkConst("y")
        val sameCtor = listOf(mkAdtConstructorApp(boxCtor, listOf(x)), mkAdtConstructorApp(boxCtor, listOf(y)))
        assertEquals(mkNot(mkEq(x, y)), simplifier.apply(mkDistinctNoSimplify(sameCtor)))
    }

    @Test
    fun testArraySelectOverStoreWithAdtIndices() = runTest({ mkColorSort() }) { color, runner ->
        val simplifier = KExprSimplifier(this)
        val red = mkAdtConstructorApp(mkAdtConstructorDecl(color, 0), emptyList())
        val green = mkAdtConstructorApp(mkAdtConstructorDecl(color, 1), emptyList())
        val arraySort = mkArraySort(color, intSort)
        val array = arraySort.mkConst("array")
        val v = intSort.mkConst("v")

        val simplified = simplifier.apply(mkArraySelectNoSimplify(mkArrayStoreNoSimplify(array, red, v), green))
        assertEquals(mkArraySelectNoSimplify(array, green), simplified)
        runner.check(mkArraySelectNoSimplify(mkArrayStoreNoSimplify(array, red, v), green), simplified) { "red green" }
    }

    private fun KContext.mkListSort(): KAdtSort = mkAdtDefinition("List") {
        addConstructor("nil")
        addConstructor("cons", "head" to intSort, "tail" to selfSort)
    }.selfSort

    private fun KContext.mkPairSort(): KAdtSort = mkAdtDefinition("Pair") {
        addConstructor("pair", "fst" to intSort, "snd" to intSort)
    }.selfSort

    private fun KContext.mkBoxSort(): KAdtSort = mkAdtDefinition("Box") {
        addConstructor("box", "v" to intSort)
    }.selfSort

    private fun KContext.mkTreeSort(): KAdtSort = mkAdtDefinition("Tree") {
        addConstructor("leaf")
        addConstructor("node", "left" to selfSort, "value" to intSort, "right" to selfSort)
    }.selfSort

    private fun KContext.mkColorSort(): KAdtSort = mkAdtDefinition("Color") {
        addConstructor("red")
        addConstructor("green")
    }.selfSort
}
