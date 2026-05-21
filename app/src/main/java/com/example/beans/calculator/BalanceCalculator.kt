package com.example.beans.calculator

import com.example.beans.model.Transaction
import java.math.BigDecimal

class BalanceCalculator {

    /**
     * Compute per-account balances from a list of transactions.
     * Returns: Map of account name -> (currency -> total balance)
     */
    fun computeBalances(
        transactions: List<Transaction>
    ): Map<String, Map<String, BigDecimal>> {
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

            // Infer missing amount: must balance to zero per currency
            if (postingsWithoutAmounts.size == 1) {
                val inferredAccount = postingsWithoutAmounts[0].account
                val totals = mutableMapOf<String, BigDecimal>()
                for (posting in postingsWithAmounts) {
                    val amount = posting.amount!!
                    totals[amount.currency] = (totals[amount.currency] ?: BigDecimal.ZERO).add(amount.value)
                }
                val accountBalances = balances.getOrPut(inferredAccount) { mutableMapOf() }
                for ((currency, total) in totals) {
                    val inferred = total.negate()
                    accountBalances[currency] = (accountBalances[currency] ?: BigDecimal.ZERO).add(inferred)
                }
            }
        }

        return balances
    }

    /**
     * Group balances by top-level account type (Assets, Liabilities, Equity, Income, Expenses).
     */
    fun groupByAccountType(
        balances: Map<String, Map<String, BigDecimal>>
    ): Map<String, Map<String, Map<String, BigDecimal>>> {
        return balances.entries.groupBy { (account, _) ->
            account.substringBefore(":")
        }.mapValues { (_, entries) ->
            entries.associate { it.key to it.value }
        }
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
}
