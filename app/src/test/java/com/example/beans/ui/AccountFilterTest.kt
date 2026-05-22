package com.example.beans.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the register account picker's filtering (issue #18).
 */
class AccountFilterTest {
    private val accounts =
        listOf(
            "Assets:Bank:Checking",
            "Assets:Bank:Savings",
            "Expenses:Food",
            "Expenses:Income Tax",
            "Income:Bank Interest",
            "Income:Salary",
        )

    @Test
    fun `blank query offers every account unchanged`() {
        assertEquals(accounts, filterAccounts(accounts, ""))
        assertEquals(accounts, filterAccounts(accounts, "   "))
    }

    @Test
    fun `query keeps only substring matches`() {
        assertEquals(listOf("Assets:Bank:Checking"), filterAccounts(accounts, "Checking"))
    }

    @Test
    fun `non-matching query yields no accounts`() {
        assertEquals(emptyList<String>(), filterAccounts(accounts, "Liabilities"))
    }

    @Test
    fun `match is case-insensitive`() {
        assertEquals(listOf("Income:Salary"), filterAccounts(accounts, "salary"))
        assertEquals(listOf("Income:Salary"), filterAccounts(accounts, "SALARY"))
    }

    @Test
    fun `accounts starting with the query rank before mid-string matches`() {
        assertEquals(
            listOf("Income:Bank Interest", "Income:Salary", "Expenses:Income Tax"),
            filterAccounts(accounts, "Income"),
        )
    }

    @Test
    fun `surrounding whitespace in the query is ignored`() {
        assertEquals(listOf("Expenses:Food"), filterAccounts(accounts, "  Food  "))
    }
}
