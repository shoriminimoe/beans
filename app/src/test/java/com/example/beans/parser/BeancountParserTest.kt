package com.example.beans.parser

import com.example.beans.model.Amount
import com.example.beans.model.Posting
import com.example.beans.model.Transaction
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BeancountParserTest {

    private val parser = BeancountParser()

    @Test
    fun `parse single transaction with two postings`() {
        val input = """
            2024-01-15 * "Grocery Store" "Weekly groceries"
              Expenses:Food:Groceries  45.50 USD
              Assets:Bank:Checking
        """.trimIndent()

        val file = parser.parse(input)
        val txns = file.transactions

        assertEquals(1, txns.size)
        val txn = txns[0]
        assertEquals(LocalDate.of(2024, 1, 15), txn.date)
        assertEquals("*", txn.flag)
        assertEquals("Grocery Store", txn.payee)
        assertEquals("Weekly groceries", txn.narration)
        assertEquals(2, txn.postings.size)

        assertEquals("Expenses:Food:Groceries", txn.postings[0].account)
        assertEquals(BigDecimal("45.50"), txn.postings[0].amount?.value)
        assertEquals("USD", txn.postings[0].amount?.currency)

        assertEquals("Assets:Bank:Checking", txn.postings[1].account)
        assertNull(txn.postings[1].amount)
    }

    @Test
    fun `parse transaction with narration only (no payee)`() {
        val input = """
            2024-02-01 * "Bank transfer"
              Assets:Bank:Savings  500.00 USD
              Assets:Bank:Checking
        """.trimIndent()

        val file = parser.parse(input)
        val txn = file.transactions[0]
        assertEquals("", txn.payee)
        assertEquals("Bank transfer", txn.narration)
    }

    @Test
    fun `parse transaction with pending flag`() {
        val input = """
            2024-03-01 ! "Store" "Pending purchase"
              Expenses:Misc  10.00 EUR
              Liabilities:CreditCard
        """.trimIndent()

        val file = parser.parse(input)
        assertEquals("!", file.transactions[0].flag)
    }

    @Test
    fun `parse multiple transactions`() {
        val input = """
            2024-01-01 * "Store A" "Groceries"
              Expenses:Food  20.00 USD
              Assets:Cash

            2024-01-02 * "Store B" "Coffee"
              Expenses:Food:Coffee  5.00 USD
              Assets:Cash
        """.trimIndent()

        val file = parser.parse(input)
        assertEquals(2, file.transactions.size)
        assertEquals("Store A", file.transactions[0].payee)
        assertEquals("Store B", file.transactions[1].payee)
    }

    @Test
    fun `parse preserves comments and directives`() {
        val input = """
            ; This is a comment
            option "operating_currency" "USD"

            2024-01-01 * "Store" "Stuff"
              Expenses:Misc  10.00 USD
              Assets:Cash
        """.trimIndent()

        val file = parser.parse(input)
        assertEquals(1, file.transactions.size)
        // Non-transaction entries should be preserved
        assertTrue(file.entries.size > 1)
    }

    @Test
    fun `parse negative amount`() {
        val input = """
            2024-01-15 * "Refund" "Got money back"
              Expenses:Food  -20.00 USD
              Assets:Bank:Checking
        """.trimIndent()

        val file = parser.parse(input)
        val posting = file.transactions[0].postings[0]
        assertEquals(BigDecimal("-20.00"), posting.amount?.value)
    }

    @Test
    fun `parse posting with both amounts explicit`() {
        val input = """
            2024-01-15 * "Transfer" "Move money"
              Assets:Bank:Savings  500.00 USD
              Assets:Bank:Checking  -500.00 USD
        """.trimIndent()

        val file = parser.parse(input)
        val txn = file.transactions[0]
        assertNotNull(txn.postings[0].amount)
        assertNotNull(txn.postings[1].amount)
        assertEquals(BigDecimal("-500.00"), txn.postings[1].amount?.value)
    }

    @Test
    fun `serialize single transaction`() {
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
        val file = BeancountFile(listOf(TransactionEntry(txn)))
        val output = parser.serialize(file)

        assertTrue(output.contains("2024-01-15 * \"Grocery Store\" \"Weekly groceries\""))
        assertTrue(output.contains("  Expenses:Food:Groceries  45.50 USD"))
        assertTrue(output.contains("  Assets:Bank:Checking"))
    }

    @Test
    fun `serialize transaction with narration only`() {
        val txn = Transaction(
            date = LocalDate.of(2024, 1, 1),
            flag = "*",
            payee = "",
            narration = "Transfer",
            postings = listOf(
                Posting("Assets:A", Amount(BigDecimal("100.00"), "USD")),
                Posting("Assets:B", null)
            )
        )
        val file = BeancountFile(listOf(TransactionEntry(txn)))
        val output = parser.serialize(file)

        assertTrue(output.contains("2024-01-01 * \"Transfer\""))
        assertFalse(output.contains("\"\" \"Transfer\""))
    }

    @Test
    fun `round-trip preserves content`() {
        val input = """
            ; My ledger
            option "operating_currency" "USD"

            2024-01-01 * "Store" "Groceries"
              Expenses:Food  20.00 USD
              Assets:Cash

            2024-01-02 * "Coffee Shop" "Latte"
              Expenses:Food:Coffee  5.00 USD
              Assets:Cash
        """.trimIndent()

        val file = parser.parse(input)
        val output = parser.serialize(file)
        val reparsed = parser.parse(output)

        assertEquals(file.transactions.size, reparsed.transactions.size)
        for (i in file.transactions.indices) {
            val orig = file.transactions[i]
            val re = reparsed.transactions[i]
            assertEquals(orig.date, re.date)
            assertEquals(orig.payee, re.payee)
            assertEquals(orig.narration, re.narration)
            assertEquals(orig.postings.size, re.postings.size)
        }
    }

    @Test
    fun `parse empty input`() {
        val file = parser.parse("")
        assertTrue(file.transactions.isEmpty())
        assertTrue(file.entries.isEmpty())
    }

    @Test
    fun `parse file with only comments`() {
        val input = """
            ; Comment line 1
            ; Comment line 2
        """.trimIndent()

        val file = parser.parse(input)
        assertTrue(file.transactions.isEmpty())
        assertEquals(2, file.entries.size)
    }

    @Test
    fun `parse transaction with multiple currencies`() {
        val input = """
            2024-06-01 * "Exchange" "Buy euros"
              Assets:EUR  100.00 EUR
              Assets:USD  -110.00 USD
        """.trimIndent()

        val file = parser.parse(input)
        val txn = file.transactions[0]
        assertEquals("EUR", txn.postings[0].amount?.currency)
        assertEquals("USD", txn.postings[1].amount?.currency)
    }
}
