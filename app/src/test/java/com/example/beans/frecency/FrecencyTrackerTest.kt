package com.example.beans.frecency

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FrecencyTrackerTest {
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var tracker: FrecencyTracker

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        tracker = FrecencyTracker(prefs, clock = { 1000L })
    }

    @Test
    fun `record and retrieve payees by frecency`() {
        tracker.recordPayee("Store A")
        tracker.recordPayee("Store A")
        tracker.recordPayee("Store B")

        val suggestions = tracker.suggestPayees("")
        assertEquals("Store A", suggestions[0])
        assertEquals("Store B", suggestions[1])
    }

    @Test
    fun `recent items score higher than older frequent items`() {
        val tracker = FrecencyTracker(prefs, clock = { time })

        // Record "Old" a few times far in the past
        time = 1000L
        repeat(3) { tracker.recordPayee("Old") }

        // Record "New" once very recently (365 days later — way past the 7-day half-life)
        time = 365L * 24 * 60 * 60 * 1000
        tracker.recordPayee("New")

        val suggestions = tracker.suggestPayees("")
        assertEquals("New", suggestions[0])
    }

    @Test
    fun `suggest payees filters by prefix`() {
        tracker.recordPayee("Coffee Shop")
        tracker.recordPayee("Corner Store")
        tracker.recordPayee("Bakery")

        val suggestions = tracker.suggestPayees("Co")
        assertEquals(2, suggestions.size)
        assertTrue(suggestions.contains("Coffee Shop"))
        assertTrue(suggestions.contains("Corner Store"))
    }

    @Test
    fun `suggest payees case insensitive`() {
        tracker.recordPayee("Coffee Shop")

        val suggestions = tracker.suggestPayees("coffee")
        assertEquals(1, suggestions.size)
        assertEquals("Coffee Shop", suggestions[0])
    }

    @Test
    fun `record and retrieve accounts by frecency`() {
        tracker.recordAccount("Expenses:Food")
        tracker.recordAccount("Expenses:Food")
        tracker.recordAccount("Assets:Cash")

        val suggestions = tracker.suggestAccounts("")
        assertEquals("Expenses:Food", suggestions[0])
        assertEquals("Assets:Cash", suggestions[1])
    }

    @Test
    fun `suggest accounts filters by prefix`() {
        tracker.recordAccount("Expenses:Food")
        tracker.recordAccount("Expenses:Food:Coffee")
        tracker.recordAccount("Assets:Cash")

        val suggestions = tracker.suggestAccounts("Exp")
        assertEquals(2, suggestions.size)
        assertTrue(suggestions.all { it.startsWith("Exp") })
    }

    @Test
    fun `empty prefix returns all sorted by frecency`() {
        tracker.recordPayee("A")
        tracker.recordPayee("B")
        tracker.recordPayee("B")

        val suggestions = tracker.suggestPayees("")
        assertEquals(2, suggestions.size)
        assertEquals("B", suggestions[0])
    }

    @Test
    fun `no suggestions when nothing recorded`() {
        assertTrue(tracker.suggestPayees("foo").isEmpty())
        assertTrue(tracker.suggestAccounts("foo").isEmpty())
    }

    @Test
    fun `delimiter-incremental account completion returns next segment`() {
        tracker.recordAccount("Expenses:Food:Coffee")
        tracker.recordAccount("Expenses:Food:Groceries")
        tracker.recordAccount("Expenses:Transport")
        tracker.recordAccount("Assets:Bank:Checking")

        // Typing "Expenses:" should suggest the next segment options
        val completions = tracker.completeAccountIncremental("Expenses:")
        assertTrue(completions.contains("Expenses:Food"))
        assertTrue(completions.contains("Expenses:Transport"))
        // Should not include full deep paths, just next segment
        assertFalse(completions.contains("Expenses:Food:Coffee"))
    }

    @Test
    fun `incremental completion from deeper prefix`() {
        tracker.recordAccount("Expenses:Food:Coffee")
        tracker.recordAccount("Expenses:Food:Groceries")

        val completions = tracker.completeAccountIncremental("Expenses:Food:")
        assertTrue(completions.contains("Expenses:Food:Coffee"))
        assertTrue(completions.contains("Expenses:Food:Groceries"))
    }

    @Test
    fun `incremental completion with no colon returns top-level`() {
        tracker.recordAccount("Expenses:Food")
        tracker.recordAccount("Assets:Bank")
        tracker.recordAccount("Income:Salary")

        val completions = tracker.completeAccountIncremental("")
        assertTrue(completions.contains("Expenses"))
        assertTrue(completions.contains("Assets"))
        assertTrue(completions.contains("Income"))
    }

    @Test
    fun `incremental completion sorted by frecency`() {
        val tracker = FrecencyTracker(prefs, clock = { 1000L })
        tracker.recordAccount("Expenses:Food")
        tracker.recordAccount("Expenses:Food")
        tracker.recordAccount("Expenses:Transport")

        val completions = tracker.completeAccountIncremental("Expenses:")
        assertEquals("Expenses:Food", completions[0])
        assertEquals("Expenses:Transport", completions[1])
    }

    @Test
    fun `persistence survives new tracker instance`() {
        tracker.recordPayee("Persistent Payee")
        tracker.recordAccount("Persistent:Account")

        val tracker2 = FrecencyTracker(prefs, clock = { 1000L })
        assertEquals(1, tracker2.suggestPayees("").size)
        assertEquals("Persistent Payee", tracker2.suggestPayees("")[0])
        assertEquals(1, tracker2.suggestAccounts("").size)
    }

    companion object {
        var time = 1000L
    }
}

/**
 * Minimal fake SharedPreferences for unit testing (no Android context needed).
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = data[key] as? String ?: defValue

    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = data[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = data[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = data[key] as? Float ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = data[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor =
        object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(
                key: String?,
                value: String?,
            ): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor {
                key?.let { pending[it] = values }
                return this
            }

            override fun putInt(
                key: String?,
                value: Int,
            ): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }

            override fun putLong(
                key: String?,
                value: Long,
            ): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }

            override fun putFloat(
                key: String?,
                value: Float,
            ): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ): SharedPreferences.Editor {
                key?.let { pending[it] = value }
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let { removals.add(it) }
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }

            override fun commit(): Boolean {
                doApply()
                return true
            }

            override fun apply() {
                doApply()
            }

            private fun doApply() {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                data.putAll(pending)
            }
        }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}
