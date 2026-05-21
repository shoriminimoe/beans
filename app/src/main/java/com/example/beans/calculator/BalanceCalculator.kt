package com.example.beans.calculator

import com.example.beans.model.RegisterEntry
import com.example.beans.model.Transaction
import java.math.BigDecimal

class BalanceCalculator {
    /**
     * Compute per-account balances from a list of transactions.
     * Returns: Map of account name -> (currency -> total balance)
     */
    fun computeBalances(transactions: List<Transaction>): Map<String, Map<String, BigDecimal>> {
        val balances = mutableMapOf<String, MutableMap<String, BigDecimal>>()

        for (txn in transactions) {
            val postingsWithAmounts = txn.postings.filter { it.amount != null }
            val postingsWithoutAmounts = txn.postings.filter { it.amount == null }

            // Add explicit amounts
            for (posting in postingsWithAmounts) {
                val amount = posting.amount!!
                val accountBalances = balances.getOrPut(posting.account) { mutableMapOf() }
                accountBalances[amount.currency] = (accountBalances[amount.currency] ?: BigDecimal.ZERO).add(amount.value)
            }

            // Infer the single missing amount so the transaction nets to zero.
            if (postingsWithoutAmounts.size == 1) {
                val inferredAccount = postingsWithoutAmounts[0].account
                val accountBalances = balances.getOrPut(inferredAccount) { mutableMapOf() }
                for ((currency, inferred) in inferredAmounts(txn)) {
                    accountBalances[currency] = (accountBalances[currency] ?: BigDecimal.ZERO).add(inferred)
                }
            }
        }

        return balances
    }

    /**
     * Group balances by top-level account type (Assets, Liabilities, Equity, Income, Expenses).
     */
    fun groupByAccountType(balances: Map<String, Map<String, BigDecimal>>): Map<String, Map<String, Map<String, BigDecimal>>> =
        balances.entries
            .groupBy { (account, _) ->
                account.substringBefore(":")
            }.mapValues { (_, entries) ->
                entries.associate { it.key to it.value }
            }

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

    /**
     * Build a register for [account]: every transaction touching that account
     * or any sub-account, chronological (oldest first), each carrying the
     * transaction's per-currency net effect on the account and the running
     * balance after it. A single missing posting amount is inferred the same
     * way [computeBalances] infers it.
     */
    fun computeRegister(
        transactions: List<Transaction>,
        account: String,
    ): List<RegisterEntry> {
        fun inScope(acc: String): Boolean = acc == account || acc.startsWith("$account:")

        val included =
            transactions
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

            // Inferred amount: a single missing posting balances the txn.
            if (postingsWithoutAmounts.size == 1 &&
                inScope(postingsWithoutAmounts[0].account)
            ) {
                for ((currency, inferred) in inferredAmounts(txn)) {
                    change[currency] =
                        (change[currency] ?: BigDecimal.ZERO).add(inferred)
                }
            }

            for ((currency, value) in change) {
                running[currency] = (running[currency] ?: BigDecimal.ZERO).add(value)
            }

            entries.add(
                RegisterEntry(
                    transaction = txn,
                    change = change.toMap(),
                    balance = running.toMap(),
                ),
            )
        }

        return entries
    }

    /**
     * The per-currency amounts of a transaction's single amount-less posting.
     *
     * Beancount requires every transaction to net to zero per currency, so a
     * lone posting without an explicit amount takes the negation of the
     * per-currency totals of the postings that do have amounts. Returns an
     * empty map unless exactly one posting is missing an amount — with zero,
     * or two or more, there is nothing (or too much) to infer.
     *
     * Shared by [computeBalances] and [computeRegister] so both apply the
     * inference rule identically.
     */
    private fun inferredAmounts(txn: Transaction): Map<String, BigDecimal> {
        if (txn.postings.count { it.amount == null } != 1) return emptyMap()

        val totals = mutableMapOf<String, BigDecimal>()
        for (posting in txn.postings) {
            val amount = posting.amount ?: continue
            totals[amount.currency] = (totals[amount.currency] ?: BigDecimal.ZERO).add(amount.value)
        }
        return totals.mapValues { (_, total) -> total.negate() }
    }
}
