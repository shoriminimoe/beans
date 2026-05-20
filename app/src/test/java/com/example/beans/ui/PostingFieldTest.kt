package com.example.beans.ui

import org.junit.Assert.*
import org.junit.Test

class PostingFieldTest {

    @Test
    fun `duplicate posting field copies all values`() {
        val original = PostingField(
            account = "Expenses:Food",
            amount = "25.00",
            currency = "USD"
        )
        val duplicate = original.copy()
        assertEquals(original.account, duplicate.account)
        assertEquals(original.amount, duplicate.amount)
        assertEquals(original.currency, duplicate.currency)
    }

    @Test
    fun `duplicate posting field is independent`() {
        val original = PostingField(
            account = "Expenses:Food",
            amount = "25.00",
            currency = "USD"
        )
        val duplicate = original.copy(amount = "50.00")
        assertEquals("25.00", original.amount)
        assertEquals("50.00", duplicate.amount)
    }

    @Test
    fun `duplicate empty posting field`() {
        val original = PostingField()
        val duplicate = original.copy()
        assertEquals("", duplicate.account)
        assertEquals("", duplicate.amount)
        assertEquals("USD", duplicate.currency)
    }

    @Test
    fun `inserting duplicate into posting list`() {
        val postings = mutableListOf(
            PostingField("Expenses:Food", "20.00", "USD"),
            PostingField("Assets:Cash", "", "USD")
        )
        val toDuplicate = postings[0]
        postings.add(1, toDuplicate.copy())
        assertEquals(3, postings.size)
        assertEquals("Expenses:Food", postings[1].account)
        assertEquals("20.00", postings[1].amount)
    }
}
