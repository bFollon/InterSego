package com.github.bfollon.linecapp.utils

import com.github.bfollon.linecapp.utils.MapUtils.accumulateWith
import com.github.bfollon.linecapp.utils.MapUtils.mergeWith
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MapUtilsTest : FunSpec({

    context("mergeWith") {
        test("disjoint keys combines both maps") {
            val a = mapOf("a" to 1, "b" to 2)
            val b = mapOf("c" to 3, "d" to 4)
            a.mergeWith(b) { va, vb -> va + vb } shouldBe mapOf("a" to 1, "b" to 2, "c" to 3, "d" to 4)
        }

        test("overlapping keys uses combine function") {
            val a = mapOf("a" to 1, "b" to 2)
            val b = mapOf("b" to 10, "c" to 3)
            a.mergeWith(b) { va, vb -> va + vb } shouldBe mapOf("a" to 1, "b" to 12, "c" to 3)
        }

        test("empty other map returns original") {
            val a = mapOf("a" to 1)
            a.mergeWith(emptyMap()) { va, vb -> va + vb } shouldBe mapOf("a" to 1)
        }

        test("empty original map returns other") {
            val b = mapOf("b" to 2)
            emptyMap<String, Int>().mergeWith(b) { va, vb -> va + vb } shouldBe mapOf("b" to 2)
        }
    }

    context("accumulateWith") {
        test("builds lists from single values") {
            val base = emptyMap<String, List<Int>>()
            val items = mapOf("a" to 1, "b" to 2)
            val result = base.accumulateWith(items)
            result shouldBe mapOf("a" to listOf(1), "b" to listOf(2))
        }

        test("appends to existing lists") {
            val base = mapOf("a" to listOf(1))
            val items = mapOf("a" to 2, "b" to 3)
            val result = base.accumulateWith(items)
            result shouldBe mapOf("a" to listOf(1, 2), "b" to listOf(3))
        }

        test("empty other returns original") {
            val base = mapOf("a" to listOf(1))
            base.accumulateWith(emptyMap()) shouldBe mapOf("a" to listOf(1))
        }
    }
})
