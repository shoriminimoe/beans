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

    private fun txnOn(date: LocalDate, vararg postings: Posting) = Transaction(
        date = date,
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

    @Test
    fun `selectable accounts include ancestor prefixes`() {
        val transactions = listOf(
            txn(
                Posting("Assets:Bank:Checking", Amount(BigDecimal("100.00"), "USD")),
                Posting("Expenses:Food", null)
            )
        )

        val accounts = calculator.selectableAccounts(transactions)

        assertEquals(
            listOf("Assets", "Assets:Bank", "Assets:Bank:Checking", "Expenses", "Expenses:Food"),
            accounts
        )
    }

    @Test
    fun `selectable accounts are sorted and deduplicated`() {
        val transactions = listOf(
            txn(
                Posting("Assets:Cash", Amount(BigDecimal("10.00"), "USD")),
                Posting("Expenses:Food", null)
            ),
            txn(
                Posting("Assets:Cash", Amount(BigDecimal("20.00"), "USD")),
                Posting("Expenses:Food", null)
            )
        )

        val accounts = calculator.selectableAccounts(transactions)

        assertEquals(listOf("Assets", "Assets:Cash", "Expenses", "Expenses:Food"), accounts)
    }

    @Test
    fun `selectable accounts empty for no transactions`() {
        assertTrue(calculator.selectableAccounts(emptyList()).isEmpty())
    }

    @Test
    fun `register accumulates running balance`() {
        val transactions = listOf(
            txnOn(
                LocalDate.of(2024, 1, 1),
                Posting("Assets:Cash", Amount(BigDecimal("100.00"), "USD")),
                Posting("Equity:Opening", Amount(BigDecimal("-100.00"), "USD"))
            ),
            txnOn(
                LocalDate.of(2024, 1, 2),
                Posting("Expenses:Food", Amount(BigDecimal("30.00"), "USD")),
                Posting("Assets:Cash", Amount(BigDecimal("-30.00"), "USD"))
            ),
            txnOn(
                LocalDate.of(2024, 1, 3),
                Posting("Assets:Cash", Amount(BigDecimal("50.00"), "USD")),
                Posting("Income:Gift", Amount(BigDecimal("-50.00"), "USD"))
            )
        )

        val register = calculator.computeRegister(transactions, "Assets:Cash")

        assertEquals(3, register.size)
        assertEquals(BigDecimal("100.00"), register[0].balance["USD"])
        assertEquals(BigDecimal("70.00"), register[1].balance["USD"])
        assertEquals(BigDecimal("120.00"), register[2].balance["USD"])
        assertEquals(BigDecimal("-30.00"), register[1].change["USD"])
    }

    @Test
    fun `register rolls up sub-accounts`() {
        val transactions = listOf(
            txnOn(
                LocalDate.of(2024, 1, 1),
                Posting("Assets:Bank:Checking", Amount(BigDecimal("200.00"), "USD")),
                Posting("Equity:Opening", Amount(BigDecimal("-200.00"), "USD"))
            ),
            txnOn(
                LocalDate.of(2024, 1, 2),
                Posting("Assets:Bank:Savings", Amount(BigDecimal("300.00"), "USD")),
                Posting("Equity:Opening", Amount(BigDecimal("-300.00"), "USD"))
            )
        )

        val register = calculator.computeRegister(transactions, "Assets:Bank")

        assertEquals(2, register.size)
        assertEquals(BigDecimal("200.00"), register[0].balance["USD"])
        assertEquals(BigDecimal("500.00"), register[1].balance["USD"])
    }

    @Test
    fun `register keeps currencies separate`() {
        val transactions = listOf(
            txnOn(
                LocalDate.of(2024, 1, 1),
                Posting("Assets:Wallet", Amount(BigDecimal("100.00"), "USD")),
                Posting("Equity:Opening", Amount(BigDecimal("-100.00"), "USD"))
            ),
            txnOn(
                LocalDate.of(2024, 1, 2),
                Posting("Assets:Wallet", Amount(BigDecimal("80.00"), "EUR")),
                Posting("Equity:Opening", Amount(BigDecimal("-80.00"), "EUR"))
            )
        )

        val register = calculator.computeRegister(transactions, "Assets:Wallet")

        assertEquals(2, register.size)
        assertEquals(BigDecimal("100.00"), register[0].balance["USD"])
        assertNull(register[0].balance["EUR"])
        assertEquals(BigDecimal("100.00"), register[1].balance["USD"])
        assertEquals(BigDecimal("80.00"), register[1].balance["EUR"])
    }

    @Test
    fun `register infers missing posting amount`() {
        val transactions = listOf(
            txnOn(
                LocalDate.of(2024, 1, 1),
                Posting("Expenses:Food", Amount(BigDecimal("40.00"), "USD")),
                Posting("Assets:Cash", null)
            )
        )

        val register = calculator.computeRegister(transactions, "Assets:Cash")

        assertEquals(1, register.size)
        assertEquals(BigDecimal("-40.00"), register[0].change["USD"])
        assertEquals(BigDecimal("-40.00"), register[0].balance["USD"])
    }

    @Test
    fun `register sorts transactions chronologically`() {
        val transactions = listOf(
            txnOn(
                LocalDate.of(2024, 3, 1),
                Posting("Assets:Cash", Amount(BigDecimal("30.00"), "USD")),
                Posting("Income:Gift", null)
            ),
            txnOn(
                LocalDate.of(2024, 1, 1),
                Posting("Assets:Cash", Amount(BigDecimal("10.00"), "USD")),
                Posting("Income:Gift", null)
            ),
            txnOn(
                LocalDate.of(2024, 2, 1),
                Posting("Assets:Cash", Amount(BigDecimal("20.00"), "USD")),
                Posting("Income:Gift", null)
            )
        )

        val register = calculator.computeRegister(transactions, "Assets:Cash")

        assertEquals(LocalDate.of(2024, 1, 1), register[0].transaction.date)
        assertEquals(LocalDate.of(2024, 2, 1), register[1].transaction.date)
        assertEquals(LocalDate.of(2024, 3, 1), register[2].transaction.date)
        assertEquals(BigDecimal("10.00"), register[0].balance["USD"])
        assertEquals(BigDecimal("30.00"), register[1].balance["USD"])
        assertEquals(BigDecimal("60.00"), register[2].balance["USD"])
    }

    @Test
    fun `register excludes sibling accounts sharing a name prefix`() {
        val transactions = listOf(
            txnOn(
                LocalDate.of(2024, 1, 1),
                Posting("Assets:Bank", Amount(BigDecimal("100.00"), "USD")),
                Posting("Equity:Opening", null)
            ),
            txnOn(
                LocalDate.of(2024, 1, 2),
                Posting("Assets:BankFee", Amount(BigDecimal("5.00"), "USD")),
                Posting("Equity:Opening", null)
            )
        )

        val register = calculator.computeRegister(transactions, "Assets:Bank")

        assertEquals(1, register.size)
        assertEquals(BigDecimal("100.00"), register[0].balance["USD"])
    }

    @Test
    fun `register is empty for account with no transactions`() {
        val transactions = listOf(
            txnOn(
                LocalDate.of(2024, 1, 1),
                Posting("Assets:Cash", Amount(BigDecimal("100.00"), "USD")),
                Posting("Equity:Opening", null)
            )
        )

        val register = calculator.computeRegister(transactions, "Assets:Bank")

        assertTrue(register.isEmpty())
    }
}
