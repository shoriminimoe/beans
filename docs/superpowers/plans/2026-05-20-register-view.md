# Register View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a register view that lets the user pick an account and see its transactions chronologically with a running balance.

**Architecture:** Mirror the existing Balance Sheet feature's three-layer pattern — pure logic in `BalanceCalculator`, state in a new `RegisterViewModel`, UI in a new `RegisterScreen` wired into the `NavHost`. The register computation reuses the same single-missing-amount inference the calculator already uses for balances.

**Tech Stack:** Kotlin, Jetpack Compose (Material3, BOM 2024.12.01), Navigation Compose, JUnit 4 unit tests. Android module is `app`.

---

## Background for the implementer

The app edits beancount ledger files. Key existing types (do not change them):

- `com.example.beans.model.Transaction` — `date: LocalDate`, `flag: String`, `payee: String`, `narration: String`, `postings: List<Posting>`, `id: String` (UUID default).
- `com.example.beans.model.Posting` — `account: String`, `amount: Amount?` (null means the amount is inferred).
- `com.example.beans.model.Amount` — `value: BigDecimal`, `currency: String`.
- `com.example.beans.calculator.BalanceCalculator` — pure functions; `computeBalances` already infers a single missing posting amount as "everything must net to zero per currency".
- `com.example.beans.repository.BeancountRepository.getTransactions(): List<Transaction>` — transactions in file order.

A beancount account is colon-delimited (`Assets:Bank:Checking`). "Sub-accounts" of `Assets:Bank` are accounts that start with `Assets:Bank:`.

Unit tests live in `app/src/test/java/...` (plain JVM JUnit 4). Run them with:
`./gradlew :app:testDebugUnitTest`

---

## File Structure

- Create: `app/src/main/java/com/example/beans/model/RegisterEntry.kt` — register row data type.
- Modify: `app/src/main/java/com/example/beans/calculator/BalanceCalculator.kt` — add `selectableAccounts` and `computeRegister`.
- Create: `app/src/main/java/com/example/beans/viewmodel/RegisterViewModel.kt` — register screen state.
- Create: `app/src/main/java/com/example/beans/ui/RegisterScreen.kt` — register UI.
- Modify: `app/src/main/java/com/example/beans/ui/BalanceSheetScreen.kt` — make `balanceColor` non-private so the register reuses it.
- Modify: `app/src/main/java/com/example/beans/ui/TransactionListScreen.kt` — add a Register top-bar action.
- Modify: `app/src/main/java/com/example/beans/ui/MainActivity.kt` — construct the view model, add the `register` route.
- Modify: `app/src/test/java/com/example/beans/calculator/BalanceCalculatorTest.kt` — add tests + a dated `txnOn` helper.
- Create: `app/src/test/java/com/example/beans/viewmodel/RegisterViewModelTest.kt` — view model tests.

---

## Task 1: `BalanceCalculator.selectableAccounts`

Returns the sorted, deduplicated list of every posting account plus every ancestor prefix — the accounts the register's picker offers.

**Files:**
- Modify: `app/src/main/java/com/example/beans/calculator/BalanceCalculator.kt`
- Test: `app/src/test/java/com/example/beans/calculator/BalanceCalculatorTest.kt`

- [ ] **Step 1: Write the failing tests**

Add these three test methods inside the `BalanceCalculatorTest` class in `app/src/test/java/com/example/beans/calculator/BalanceCalculatorTest.kt`, after the existing `infer amount with multiple explicit postings` test:

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.beans.calculator.BalanceCalculatorTest"`
Expected: FAIL — compilation error, `selectableAccounts` unresolved.

- [ ] **Step 3: Implement `selectableAccounts`**

Add this method inside the `BalanceCalculator` class in `app/src/main/java/com/example/beans/calculator/BalanceCalculator.kt`, after `groupByAccountType`:

```kotlin
    /**
     * All accounts selectable in the register: every posting account plus every
     * ancestor prefix (so "Assets:Bank" is offered even when only
     * "Assets:Bank:Checking" has postings). Sorted and deduplicated.
     */
    fun selectableAccounts(transactions: List<Transaction>): List<String> {
        val accounts = mutableSetOf<String>()
        for (txn in transactions) {
            for (posting in txn.postings) {
                val parts = posting.account.split(":")
                for (i in parts.indices) {
                    accounts.add(parts.subList(0, i + 1).joinToString(":"))
                }
            }
        }
        return accounts.sorted()
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.beans.calculator.BalanceCalculatorTest"`
Expected: PASS — all tests in the class green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/beans/calculator/BalanceCalculator.kt app/src/test/java/com/example/beans/calculator/BalanceCalculatorTest.kt
git commit -m "feat: add selectableAccounts to BalanceCalculator"
```

---

## Task 2: `RegisterEntry` model + `BalanceCalculator.computeRegister`

`computeRegister` returns the register rows for one account: transactions touching that account or any sub-account, oldest first, each with the per-currency change and running balance.

**Files:**
- Create: `app/src/main/java/com/example/beans/model/RegisterEntry.kt`
- Modify: `app/src/main/java/com/example/beans/calculator/BalanceCalculator.kt`
- Test: `app/src/test/java/com/example/beans/calculator/BalanceCalculatorTest.kt`

- [ ] **Step 1: Create the `RegisterEntry` data class**

Create `app/src/main/java/com/example/beans/model/RegisterEntry.kt`:

```kotlin
package com.example.beans.model

import java.math.BigDecimal

/**
 * One row of an account register: a transaction, its net effect on the account
 * (per currency), and the running balance after it (per currency).
 */
data class RegisterEntry(
    val transaction: Transaction,
    val change: Map<String, BigDecimal>,
    val balance: Map<String, BigDecimal>
)
```

- [ ] **Step 2: Add the dated test helper and write the failing tests**

In `app/src/test/java/com/example/beans/calculator/BalanceCalculatorTest.kt`, add this helper right after the existing `txn(...)` helper:

```kotlin
    private fun txnOn(date: LocalDate, vararg postings: Posting) = Transaction(
        date = date,
        flag = "*",
        payee = "Test",
        narration = "Test",
        postings = postings.toList()
    )
```

Then add these test methods inside the class, after the `selectable accounts empty for no transactions` test:

```kotlin
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
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.beans.calculator.BalanceCalculatorTest"`
Expected: FAIL — compilation error, `computeRegister` unresolved.

- [ ] **Step 4: Implement `computeRegister`**

In `app/src/main/java/com/example/beans/calculator/BalanceCalculator.kt`, add this import at the top with the other imports:

```kotlin
import com.example.beans.model.RegisterEntry
```

Then add this method inside the `BalanceCalculator` class, after `selectableAccounts`:

```kotlin
    /**
     * Build a register for [account]: every transaction touching that account
     * or any sub-account, chronological (oldest first), each carrying the
     * transaction's per-currency net effect on the account and the running
     * balance after it. A single missing posting amount is inferred the same
     * way [computeBalances] infers it.
     */
    fun computeRegister(
        transactions: List<Transaction>,
        account: String
    ): List<RegisterEntry> {
        fun inScope(acc: String): Boolean =
            acc == account || acc.startsWith("$account:")

        val included = transactions
            .filter { txn -> txn.postings.any { inScope(it.account) } }
            .sortedBy { it.date }

        val running = mutableMapOf<String, BigDecimal>()
        val entries = mutableListOf<RegisterEntry>()

        for (txn in included) {
            val change = mutableMapOf<String, BigDecimal>()
            val postingsWithAmounts = txn.postings.filter { it.amount != null }
            val postingsWithoutAmounts = txn.postings.filter { it.amount == null }

            // Explicit amounts posted to the account scope
            for (posting in postingsWithAmounts) {
                if (!inScope(posting.account)) continue
                val amount = posting.amount!!
                change[amount.currency] =
                    (change[amount.currency] ?: BigDecimal.ZERO).add(amount.value)
            }

            // Inferred amount: a single missing posting must balance to zero
            if (postingsWithoutAmounts.size == 1 &&
                inScope(postingsWithoutAmounts[0].account)
            ) {
                val totals = mutableMapOf<String, BigDecimal>()
                for (posting in postingsWithAmounts) {
                    val amount = posting.amount!!
                    totals[amount.currency] =
                        (totals[amount.currency] ?: BigDecimal.ZERO).add(amount.value)
                }
                for ((currency, total) in totals) {
                    change[currency] =
                        (change[currency] ?: BigDecimal.ZERO).add(total.negate())
                }
            }

            for ((currency, value) in change) {
                running[currency] = (running[currency] ?: BigDecimal.ZERO).add(value)
            }

            entries.add(
                RegisterEntry(
                    transaction = txn,
                    change = change.toMap(),
                    balance = running.toMap()
                )
            )
        }

        return entries
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.beans.calculator.BalanceCalculatorTest"`
Expected: PASS — all tests in the class green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/beans/model/RegisterEntry.kt app/src/main/java/com/example/beans/calculator/BalanceCalculator.kt app/src/test/java/com/example/beans/calculator/BalanceCalculatorTest.kt
git commit -m "feat: add computeRegister to BalanceCalculator"
```

---

## Task 3: `RegisterViewModel`

Holds the picker's account list, the selected account, and the computed register entries — following `BalanceSheetViewModel`.

**Files:**
- Create: `app/src/main/java/com/example/beans/viewmodel/RegisterViewModel.kt`
- Test: `app/src/test/java/com/example/beans/viewmodel/RegisterViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/beans/viewmodel/RegisterViewModelTest.kt`:

```kotlin
package com.example.beans.viewmodel

import com.example.beans.calculator.BalanceCalculator
import com.example.beans.parser.BeancountParser
import com.example.beans.repository.BeancountRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal

class RegisterViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: BeancountRepository
    private lateinit var viewModel: RegisterViewModel

    private val sampleContent = """
        2024-01-01 * "Opening" "Initial balance"
          Assets:Bank:Checking  1000.00 USD
          Equity:Opening

        2024-01-02 * "Store" "Groceries"
          Expenses:Food  50.00 USD
          Assets:Bank:Checking

        2024-01-03 * "Salary" "Paycheck"
          Assets:Bank:Checking  3000.00 USD
          Income:Salary
    """.trimIndent()

    @Before
    fun setUp() {
        repository = BeancountRepository(BeancountParser())
        val file = tempFolder.newFile("test.beancount")
        file.writeText(sampleContent)
        repository.loadFile(file)
        viewModel = RegisterViewModel(repository, BalanceCalculator())
    }

    @Test
    fun `initial state has no selection and no entries`() {
        assertNull(viewModel.selectedAccount.value)
        assertTrue(viewModel.entries.value.isEmpty())
    }

    @Test
    fun `loadAccounts populates selectable accounts with ancestors`() {
        viewModel.loadAccounts()

        val accounts = viewModel.accounts.value
        assertTrue(accounts.contains("Assets"))
        assertTrue(accounts.contains("Assets:Bank"))
        assertTrue(accounts.contains("Assets:Bank:Checking"))
        assertTrue(accounts.contains("Expenses:Food"))
    }

    @Test
    fun `selectAccount records the selected account`() {
        viewModel.selectAccount("Assets:Bank:Checking")
        assertEquals("Assets:Bank:Checking", viewModel.selectedAccount.value)
    }

    @Test
    fun `selectAccount computes register with running balance`() {
        viewModel.selectAccount("Assets:Bank:Checking")

        val entries = viewModel.entries.value
        assertEquals(3, entries.size)
        // 1000 - 50 + 3000 = 3950
        assertEquals(BigDecimal("3950.00"), entries.last().balance["USD"])
    }

    @Test
    fun `selectAccount on parent rolls up sub-account`() {
        viewModel.selectAccount("Assets:Bank")

        val entries = viewModel.entries.value
        assertEquals(3, entries.size)
        assertEquals(BigDecimal("3950.00"), entries.last().balance["USD"])
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.beans.viewmodel.RegisterViewModelTest"`
Expected: FAIL — compilation error, `RegisterViewModel` unresolved.

- [ ] **Step 3: Implement `RegisterViewModel`**

Create `app/src/main/java/com/example/beans/viewmodel/RegisterViewModel.kt`:

```kotlin
package com.example.beans.viewmodel

import com.example.beans.calculator.BalanceCalculator
import com.example.beans.model.RegisterEntry
import com.example.beans.repository.BeancountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RegisterViewModel(
    private val repository: BeancountRepository,
    private val calculator: BalanceCalculator
) {

    private val _accounts = MutableStateFlow<List<String>>(emptyList())
    val accounts: StateFlow<List<String>> = _accounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String?>(null)
    val selectedAccount: StateFlow<String?> = _selectedAccount.asStateFlow()

    private val _entries = MutableStateFlow<List<RegisterEntry>>(emptyList())
    val entries: StateFlow<List<RegisterEntry>> = _entries.asStateFlow()

    /** Refresh the list of accounts the picker offers. */
    fun loadAccounts() {
        _accounts.value = calculator.selectableAccounts(repository.getTransactions())
    }

    /** Select [account] and recompute its register entries. */
    fun selectAccount(account: String) {
        _selectedAccount.value = account
        _entries.value = calculator.computeRegister(repository.getTransactions(), account)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.beans.viewmodel.RegisterViewModelTest"`
Expected: PASS — all five tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/beans/viewmodel/RegisterViewModel.kt app/src/test/java/com/example/beans/viewmodel/RegisterViewModelTest.kt
git commit -m "feat: add RegisterViewModel"
```

---

## Task 4: `RegisterScreen` UI

The register screen: a top bar, an account-picker dropdown, and a list of register rows. No unit test (consistent with the other Compose screens — there are none); verified by compiling.

**Files:**
- Modify: `app/src/main/java/com/example/beans/ui/BalanceSheetScreen.kt`
- Create: `app/src/main/java/com/example/beans/ui/RegisterScreen.kt`

- [ ] **Step 1: Make `balanceColor` reusable**

In `app/src/main/java/com/example/beans/ui/BalanceSheetScreen.kt`, change the `balanceColor` declaration from `private` to internal-package visible. Replace:

```kotlin
@Composable
private fun balanceColor(amount: BigDecimal): androidx.compose.ui.graphics.Color {
```

with:

```kotlin
@Composable
fun balanceColor(amount: BigDecimal): androidx.compose.ui.graphics.Color {
```

(`RegisterScreen` is in the same `com.example.beans.ui` package, so no import is needed once it is non-private.)

- [ ] **Step 2: Create `RegisterScreen`**

Create `app/src/main/java/com/example/beans/ui/RegisterScreen.kt`:

```kotlin
package com.example.beans.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.beans.model.RegisterEntry
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    accounts: List<String>,
    selectedAccount: String?,
    entries: List<RegisterEntry>,
    onAccountSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AccountPicker(
                accounts = accounts,
                selectedAccount = selectedAccount,
                onAccountSelected = onAccountSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            when {
                selectedAccount == null -> RegisterMessage("Select an account")
                entries.isEmpty() -> RegisterMessage("No transactions for this account")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.transaction.id }) { entry ->
                        RegisterRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisterMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPicker(
    accounts: List<String>,
    selectedAccount: String?,
    onAccountSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = selectedAccount ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Account") },
            placeholder = { Text("Select an account") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (account in accounts) {
                DropdownMenuItem(
                    text = { Text(account) },
                    onClick = {
                        expanded = false
                        onAccountSelected(account)
                    }
                )
            }
        }
    }
}

@Composable
private fun RegisterRow(entry: RegisterEntry) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val txn = entry.transaction

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            val headerText = buildAnnotatedString {
                append(txn.date.format(formatter))
                append(" ")
                if (txn.payee.isNotEmpty()) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(txn.payee)
                    }
                    append(" ")
                }
                append(txn.narration)
            }
            Text(text = headerText, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.change.isEmpty()) {
                        Text("—", style = MaterialTheme.typography.bodySmall)
                    } else {
                        for ((currency, amount) in entry.change) {
                            Text(
                                text = "${amount.toPlainString()} $currency",
                                style = MaterialTheme.typography.bodySmall,
                                color = balanceColor(amount)
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    for ((currency, amount) in entry.balance) {
                        Text(
                            text = "${amount.toPlainString()} $currency",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = balanceColor(amount)
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Compile to verify the screen builds**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

If `menuAnchor(MenuAnchorType.PrimaryNotEditable)` fails to resolve (older Material3), fall back to the deprecated no-arg form `.menuAnchor()` — both exist in BOM 2024.12.01; the typed form is preferred.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/beans/ui/BalanceSheetScreen.kt app/src/main/java/com/example/beans/ui/RegisterScreen.kt
git commit -m "feat: add RegisterScreen UI"
```

---

## Task 5: Wire the register into navigation

Add a Register action to the transaction list's top bar and a `register` route to the `NavHost`.

**Files:**
- Modify: `app/src/main/java/com/example/beans/ui/TransactionListScreen.kt`
- Modify: `app/src/main/java/com/example/beans/ui/MainActivity.kt`

- [ ] **Step 1: Add the Register action to `TransactionListScreen`**

In `app/src/main/java/com/example/beans/ui/TransactionListScreen.kt`:

(a) Add this import alongside the other `androidx.compose.material.icons` imports:

```kotlin
import androidx.compose.material.icons.automirrored.filled.List
```

(b) Add an `onRegisterClick` parameter. Replace the function signature:

```kotlin
fun TransactionListScreen(
    transactions: List<Transaction>,
    onAddClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onDeleteClick: (Transaction) -> Unit,
    onBalanceSheetClick: () -> Unit,
    onDuplicateClick: (Transaction) -> Unit
) {
```

with:

```kotlin
fun TransactionListScreen(
    transactions: List<Transaction>,
    onAddClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onDeleteClick: (Transaction) -> Unit,
    onBalanceSheetClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onDuplicateClick: (Transaction) -> Unit
) {
```

(c) Add the Register icon button to the top bar. Replace the `actions` block:

```kotlin
                actions = {
                    IconButton(onClick = onBalanceSheetClick) {
                        Icon(Icons.Default.AccountBalance, contentDescription = "Balance Sheet")
                    }
                }
```

with:

```kotlin
                actions = {
                    IconButton(onClick = onRegisterClick) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Register")
                    }
                    IconButton(onClick = onBalanceSheetClick) {
                        Icon(Icons.Default.AccountBalance, contentDescription = "Balance Sheet")
                    }
                }
```

- [ ] **Step 2: Wire `RegisterViewModel` and the `register` route into `MainActivity`**

In `app/src/main/java/com/example/beans/ui/MainActivity.kt`:

(a) Add this import alongside the other `com.example.beans.viewmodel` imports:

```kotlin
import com.example.beans.viewmodel.RegisterViewModel
```

(b) Construct the view model in `onCreate`. Replace:

```kotlin
        val balanceSheetViewModel = BalanceSheetViewModel(repository, calculator)
        val prefs = getSharedPreferences("beans_prefs", Context.MODE_PRIVATE)
```

with:

```kotlin
        val balanceSheetViewModel = BalanceSheetViewModel(repository, calculator)
        val registerViewModel = RegisterViewModel(repository, calculator)
        val prefs = getSharedPreferences("beans_prefs", Context.MODE_PRIVATE)
```

(c) Pass it to `BeansApp`. Replace the `setContent` call:

```kotlin
                BeansApp(
                    repository = repository,
                    transactionViewModel = transactionViewModel,
                    balanceSheetViewModel = balanceSheetViewModel,
                    prefs = prefs,
                    frecencyTracker = frecencyTracker
                )
```

with:

```kotlin
                BeansApp(
                    repository = repository,
                    transactionViewModel = transactionViewModel,
                    balanceSheetViewModel = balanceSheetViewModel,
                    registerViewModel = registerViewModel,
                    prefs = prefs,
                    frecencyTracker = frecencyTracker
                )
```

(d) Add the parameter to the `BeansApp` composable. Replace the signature:

```kotlin
fun BeansApp(
    repository: BeancountRepository,
    transactionViewModel: TransactionViewModel,
    balanceSheetViewModel: BalanceSheetViewModel,
    prefs: SharedPreferences,
    frecencyTracker: FrecencyTracker
) {
```

with:

```kotlin
fun BeansApp(
    repository: BeancountRepository,
    transactionViewModel: TransactionViewModel,
    balanceSheetViewModel: BalanceSheetViewModel,
    registerViewModel: RegisterViewModel,
    prefs: SharedPreferences,
    frecencyTracker: FrecencyTracker
) {
```

(e) Pass `onRegisterClick` to `TransactionListScreen`. Replace:

```kotlin
                    onBalanceSheetClick = {
                        balanceSheetViewModel.computeBalances()
                        navController.navigate("balanceSheet")
                    },
                    onDuplicateClick = { txn ->
```

with:

```kotlin
                    onBalanceSheetClick = {
                        balanceSheetViewModel.computeBalances()
                        navController.navigate("balanceSheet")
                    },
                    onRegisterClick = {
                        registerViewModel.loadAccounts()
                        navController.navigate("register")
                    },
                    onDuplicateClick = { txn ->
```

(f) Add the `register` route. Replace the `balanceSheet` composable block:

```kotlin
            composable("balanceSheet") {
                val grouped by balanceSheetViewModel.groupedBalances.collectAsState()
                BalanceSheetScreen(
                    groupedBalances = grouped,
                    onBack = { navController.popBackStack() }
                )
            }
```

with:

```kotlin
            composable("balanceSheet") {
                val grouped by balanceSheetViewModel.groupedBalances.collectAsState()
                BalanceSheetScreen(
                    groupedBalances = grouped,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("register") {
                val accounts by registerViewModel.accounts.collectAsState()
                val selectedAccount by registerViewModel.selectedAccount.collectAsState()
                val entries by registerViewModel.entries.collectAsState()
                RegisterScreen(
                    accounts = accounts,
                    selectedAccount = selectedAccount,
                    entries = entries,
                    onAccountSelected = { registerViewModel.selectAccount(it) },
                    onBack = { navController.popBackStack() }
                )
            }
```

- [ ] **Step 3: Build the full app to verify everything compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the full unit-test suite to confirm nothing regressed**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all tests pass, including the existing `BalanceCalculatorTest`, `BalanceSheetViewModelTest`, `TransactionViewModelTest`, and the new `RegisterViewModelTest`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/beans/ui/TransactionListScreen.kt app/src/main/java/com/example/beans/ui/MainActivity.kt
git commit -m "feat: wire register view into navigation"
```

---

## Done

The register view is complete: a Register action in the transaction list opens a screen with an account picker; selecting an account shows its transactions oldest-first with a per-currency running balance, rolling up sub-accounts.

## Self-Review Notes

- **Spec coverage:** Entry point + picker → Task 4 (`AccountPicker`) and Task 5. Sub-account roll-up → `inScope` in Task 2 (tested). Multi-currency separation → per-currency maps in Task 2 (tested). Oldest-first running balance → `sortedBy { it.date }` + running map in Task 2 (tested). Read-only rows → `RegisterRow` has no click handlers (Task 4). Calculator unit tests → Tasks 1–2. Out-of-scope items (date filtering, editing, price conversion) are intentionally absent.
- **Types:** `RegisterEntry(transaction, change, balance)`, `computeRegister(transactions, account)`, `selectableAccounts(transactions)`, `RegisterViewModel(repository, calculator)` with `loadAccounts()`/`selectAccount(account)` and `accounts`/`selectedAccount`/`entries` flows, and `RegisterScreen(accounts, selectedAccount, entries, onAccountSelected, onBack)` are used identically across every task that references them.
- **No placeholders:** every step has concrete code or an exact command.
