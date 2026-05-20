package com.example.beans.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionTest {

    @Test
    fun `create transaction with basic fields`() {
        val txn = Transaction(
            date = LocalDate.of(2024, 1, 15),
            flag = "*",
            payee = "Grocery Store",
            narration = "Weekly groceries",
            postings = listOf(
                Posting("Expenses:Food:Groceries", Amount(BigDecimal("45.50"), "USD")),
                Posting("Assets:Bank:Checking", null)
            )
        )

        assertEquals(LocalDate.of(2024, 1, 15), txn.date)
        assertEquals("*", txn.flag)
        assertEquals("Grocery Store", txn.payee)
        assertEquals("Weekly groceries", txn.narration)
        assertEquals(2, txn.postings.size)
    }

    @Test
    fun `posting with explicit amount`() {
        val posting = Posting("Expenses:Food", Amount(BigDecimal("45.50"), "USD"))
        assertEquals("Expenses:Food", posting.account)
        assertNotNull(posting.amount)
        assertEquals(BigDecimal("45.50"), posting.amount!!.value)
        assertEquals("USD", posting.amount!!.currency)
    }

    @Test
    fun `posting with no amount (inferred)`() {
        val posting = Posting("Assets:Bank:Checking", null)
        assertEquals("Assets:Bank:Checking", posting.account)
        assertNull(posting.amount)
    }

    @Test
    fun `amount equality`() {
        val a1 = Amount(BigDecimal("100.00"), "USD")
        val a2 = Amount(BigDecimal("100.00"), "USD")
        assertEquals(a1, a2)
    }

    @Test
    fun `transaction data class equality with same id`() {
        val postings = listOf(
            Posting("Expenses:Food", Amount(BigDecimal("10.00"), "USD")),
            Posting("Assets:Cash", null)
        )
        val id = "shared-id"
        val t1 = Transaction(LocalDate.of(2024, 3, 1), "*", "Shop", "Lunch", postings, id = id)
        val t2 = Transaction(LocalDate.of(2024, 3, 1), "*", "Shop", "Lunch", postings, id = id)
        assertEquals(t1, t2)
    }

    @Test
    fun `transaction with pending flag`() {
        val txn = Transaction(
            date = LocalDate.of(2024, 2, 1),
            flag = "!",
            payee = "Unknown",
            narration = "Pending charge",
            postings = listOf(
                Posting("Expenses:Misc", Amount(BigDecimal("5.00"), "EUR")),
                Posting("Liabilities:CreditCard", null)
            )
        )
        assertEquals("!", txn.flag)
    }

    @Test
    fun `transaction with empty payee`() {
        val txn = Transaction(
            date = LocalDate.of(2024, 1, 1),
            flag = "*",
            payee = "",
            narration = "Transfer",
            postings = listOf(
                Posting("Assets:Bank:Savings", Amount(BigDecimal("500.00"), "USD")),
                Posting("Assets:Bank:Checking", null)
            )
        )
        assertEquals("", txn.payee)
    }

    @Test
    fun `transaction copy with modified field`() {
        val txn = Transaction(
            date = LocalDate.of(2024, 1, 1),
            flag = "*",
            payee = "Store",
            narration = "Stuff",
            postings = listOf(
                Posting("Expenses:Misc", Amount(BigDecimal("10.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )
        val updated = txn.copy(narration = "Updated stuff")
        assertEquals("Updated stuff", updated.narration)
        assertEquals(txn.date, updated.date)
        assertEquals(txn.postings, updated.postings)
    }

    @Test
    fun `transaction has unique id`() {
        val txn1 = Transaction(
            date = LocalDate.of(2024, 1, 1),
            flag = "*",
            payee = "A",
            narration = "A",
            postings = emptyList()
        )
        val txn2 = Transaction(
            date = LocalDate.of(2024, 1, 1),
            flag = "*",
            payee = "A",
            narration = "A",
            postings = emptyList()
        )
        assertNotEquals(txn1.id, txn2.id)
    }
}
