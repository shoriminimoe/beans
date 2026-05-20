package com.example.beans.calculator

import com.example.beans.model.Amount
import com.example.beans.model.Posting
import com.example.beans.model.Transaction
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BalanceCalculatorTest {

    private val calculator = BalanceCalculator()

    private fun txn(vararg postings: Posting) = Transaction(
        date = LocalDate.of(2024, 1, 1),
        flag = "*",
        payee = "Test",
        narration = "Test",
        postings = postings.toList()
    )

    @Test
    fun `single transaction two accounts`() {
        val transactions = listOf(
            txn(
                Posting("Expenses:Food", Amount(BigDecimal("50.00"), "USD")),
                Posting("Assets:Cash", Amount(BigDecimal("-50.00"), "USD"))
            )
        )

        val balances = calculator.computeBalances(transactions)

        assertEquals(BigDecimal("50.00"), balances["Expenses:Food"]?.get("USD"))
        assertEquals(BigDecimal("-50.00"), balances["Assets:Cash"]?.get("USD"))
    }

    @Test
    fun `infer missing posting amount`() {
        val transactions = listOf(
            txn(
                Posting("Expenses:Food", Amount(BigDecimal("50.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        val balances = calculator.computeBalances(transactions)

        assertEquals(BigDecimal("50.00"), balances["Expenses:Food"]?.get("USD"))
        assertEquals(BigDecimal("-50.00"), balances["Assets:Cash"]?.get("USD"))
    }

    @Test
    fun `multiple transactions accumulate`() {
        val transactions = listOf(
            txn(
                Posting("Expenses:Food", Amount(BigDecimal("20.00"), "USD")),
                Posting("Assets:Cash", null)
            ),
            txn(
                Posting("Expenses:Food", Amount(BigDecimal("30.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        val balances = calculator.computeBalances(transactions)

        assertEquals(BigDecimal("50.00"), balances["Expenses:Food"]?.get("USD"))
        assertEquals(BigDecimal("-50.00"), balances["Assets:Cash"]?.get("USD"))
    }

    @Test
    fun `multiple currencies`() {
        val transactions = listOf(
            txn(
                Posting("Assets:EUR", Amount(BigDecimal("100.00"), "EUR")),
                Posting("Assets:USD", Amount(BigDecimal("-110.00"), "USD"))
            )
        )

        val balances = calculator.computeBalances(transactions)

        assertEquals(BigDecimal("100.00"), balances["Assets:EUR"]?.get("EUR"))
        assertEquals(BigDecimal("-110.00"), balances["Assets:USD"]?.get("USD"))
    }

    @Test
    fun `empty transactions`() {
        val balances = calculator.computeBalances(emptyList())
        assertTrue(balances.isEmpty())
    }

    @Test
    fun `accounts with same prefix are separate`() {
        val transactions = listOf(
            txn(
                Posting("Expenses:Food", Amount(BigDecimal("20.00"), "USD")),
                Posting("Assets:Cash", null)
            ),
            txn(
                Posting("Expenses:Food:Coffee", Amount(BigDecimal("5.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        val balances = calculator.computeBalances(transactions)

        assertEquals(BigDecimal("20.00"), balances["Expenses:Food"]?.get("USD"))
        assertEquals(BigDecimal("5.00"), balances["Expenses:Food:Coffee"]?.get("USD"))
        assertEquals(BigDecimal("-25.00"), balances["Assets:Cash"]?.get("USD"))
    }

    @Test
    fun `balance sheet grouped by account type`() {
        val transactions = listOf(
            txn(
                Posting("Assets:Bank", Amount(BigDecimal("1000.00"), "USD")),
                Posting("Equity:Opening", Amount(BigDecimal("-1000.00"), "USD"))
            ),
            txn(
                Posting("Expenses:Food", Amount(BigDecimal("50.00"), "USD")),
                Posting("Assets:Bank", null)
            ),
            txn(
                Posting("Liabilities:CreditCard", Amount(BigDecimal("-200.00"), "USD")),
                Posting("Assets:Bank", null)
            )
        )

        val balances = calculator.computeBalances(transactions)
        val grouped = calculator.groupByAccountType(balances)

        assertTrue(grouped.containsKey("Assets"))
        assertTrue(grouped.containsKey("Expenses"))
        assertTrue(grouped.containsKey("Equity"))
        assertTrue(grouped.containsKey("Liabilities"))

        assertTrue(grouped["Assets"]!!.containsKey("Assets:Bank"))
        assertTrue(grouped["Expenses"]!!.containsKey("Expenses:Food"))
    }

    @Test
    fun `infer amount with multiple explicit postings`() {
        val transactions = listOf(
            txn(
                Posting("Expenses:Food", Amount(BigDecimal("30.00"), "USD")),
                Posting("Expenses:Drink", Amount(BigDecimal("20.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        val balances = calculator.computeBalances(transactions)

        assertEquals(BigDecimal("30.00"), balances["Expenses:Food"]?.get("USD"))
        assertEquals(BigDecimal("20.00"), balances["Expenses:Drink"]?.get("USD"))
        assertEquals(BigDecimal("-50.00"), balances["Assets:Cash"]?.get("USD"))
    }
}
