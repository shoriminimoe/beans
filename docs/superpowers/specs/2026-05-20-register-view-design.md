# Register View — Design

**Date:** 2026-05-20
**Status:** Approved

## Goal

Add a register view to the Beans app. The user selects an account and sees a
chronological list of the transactions affecting that account, each with a
running balance.

## Background

Beans is an Android (Kotlin / Jetpack Compose) app for editing beancount ledger
files. It currently has three screens — a transaction list, a transaction
editor, and a balance sheet — and a clean three-layer pattern for the balance
sheet feature:

- `BalanceCalculator` — pure computation logic, unit-tested in isolation.
- `BalanceSheetViewModel` — exposes state via `StateFlow`.
- `BalanceSheetScreen` — Compose UI.

The register view mirrors this pattern.

## User Experience

### Entry point

A new **Register** action (a list/receipt icon) is added to the
`TransactionListScreen` top bar, next to the existing Balance Sheet icon.
Tapping it navigates to the new **Register screen**.

### Register screen

- **Account picker** at the top — an `ExposedDropdownMenuBox` listing every
  account *and* every ancestor prefix, so a parent like `Assets:Bank` is
  selectable even when only `Assets:Bank:Checking` has postings. Accounts are
  sorted alphabetically.
- **Initial state** — no account selected; the screen shows a prompt such as
  "Select an account."
- **Register list** — once an account is selected, the transactions touching
  that account (or any sub-account) are listed **oldest first**, so the running
  balance accumulates down the screen. Each row shows:
  - Date
  - Payee (bold) + narration — same style as `TransactionListScreen`
  - **Change** — the transaction's net effect on the account, per currency
  - **Running balance** — cumulative balance after the transaction, per
    currency, with each currency on its own line (as the Balance Sheet does)
- **Color** — positive/negative amounts tinted via the same `balanceColor`
  convention used in `BalanceSheetScreen`.
- **Empty state** — "No transactions for this account" when the selected
  account has none.
- Rows are **read-only**: no tap-to-edit, no delete. Editing stays in the
  transaction list. This keeps the register a focused read view.

## Architecture & Components

### 1. `BalanceCalculator.computeRegister(transactions, account)`

New pure method on the existing calculator. Returns `List<RegisterEntry>`:

```kotlin
data class RegisterEntry(
    val transaction: Transaction,
    val change: Map<String, BigDecimal>,   // net effect on account + sub-accounts, per currency
    val balance: Map<String, BigDecimal>   // running balance after this txn, per currency
)
```

Rules:

- **Account scope** — a transaction is included if any of its postings has an
  account equal to `account` or starting with `"$account:"`.
- **Change** — for an included transaction, sum all of its matching postings
  per currency. Apply the **same single-missing-amount inference** that
  `computeBalances` already uses: when exactly one posting in the transaction
  has no explicit amount, its amount is inferred as the negation of the sum of
  the others (per currency). The inferred posting can be the one inside the
  account scope.
- **Ordering** — entries are sorted by transaction date ascending; ties keep
  original file order (stable sort).
- **Running balance** — `balance` is the cumulative running sum of `change`
  across the ordered entries, per currency.

### 2. `BalanceCalculator.selectableAccounts(transactions)`

New pure helper. Returns the sorted list of all posting accounts plus all of
their ancestor prefixes — the set of accounts offered by the picker.
Example: a ledger with only `Assets:Bank:Checking` and `Expenses:Food` yields
`["Assets", "Assets:Bank", "Assets:Bank:Checking", "Expenses", "Expenses:Food"]`.

### 3. `RegisterViewModel`

New ViewModel following `BalanceSheetViewModel`. Holds:

- `accounts: StateFlow<List<String>>` — the selectable accounts.
- `selectedAccount: StateFlow<String?>` — currently selected account, `null`
  initially.
- `entries: StateFlow<List<RegisterEntry>>` — register entries for the
  selected account.

`selectAccount(name)` sets the selection and recomputes `entries` from
`repository.getTransactions()` via `BalanceCalculator`.

### 4. `RegisterScreen`

New composable following `BalanceSheetScreen`'s structure: a `Scaffold` with a
`TopAppBar` (back arrow), the account picker, and a `LazyColumn` of register
rows. Receives the accounts list, selected account, entries, an
`onAccountSelected` callback, and an `onBack` callback.

### 5. `MainActivity` wiring

- Construct a `RegisterViewModel` alongside the other view models.
- Add a `"register"` route to the `NavHost`.
- Add a Register icon + `onRegisterClick` callback to `TransactionListScreen`,
  navigating to the register route.

## Testing

`BalanceCalculatorTest` gains new cases — matching the existing test style:

- `computeRegister`: running-balance accumulation, sub-account roll-up,
  multi-currency separation, inferred-amount postings, and chronological
  ordering.
- `selectableAccounts`: ancestor-prefix expansion.

The ViewModel and UI layers stay thin enough not to need new instrumentation
tests.

## Out of Scope (YAGNI)

- Date-range filtering and search.
- Editing or deleting transactions from the register.
- Cost-basis / price conversion between currencies.

The running balance is the goal; these were not requested.
