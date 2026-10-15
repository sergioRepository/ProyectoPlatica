package com.lavidanoesunbanano.app.domain

import com.lavidanoesunbanano.app.domain.apps.AppOrdering
import com.lavidanoesunbanano.app.domain.model.AppInfo
import kotlin.test.assertEquals
import org.junit.Test

class AppOrderingTest {

    private val calculator = AppInfo("com.example.calc", "calculadora")
    private val bank = AppInfo("com.example.bank", "Banco")
    private val tree = AppInfo("com.example.tree", "Árbol")
    private val instagram = AppInfo("com.instagram.android", "Instagram")
    private val youtube = AppInfo("com.google.android.youtube", "YouTube")

    @Test
    fun `suggested apps first, then alphabetical ignoring case and accents`() {
        val sorted = AppOrdering.sort(listOf(calculator, youtube, bank, tree, instagram, bank))

        assertEquals(listOf(instagram, youtube, tree, bank, calculator), sorted)
    }

    @Test
    fun `filters by label or package`() {
        val apps = listOf(calculator, bank, instagram)

        assertEquals(listOf(instagram), AppOrdering.filter(apps, "insta"))
        assertEquals(listOf(calculator, bank), AppOrdering.filter(apps, "example"))
        assertEquals(apps, AppOrdering.filter(apps, "  "))
    }
}
