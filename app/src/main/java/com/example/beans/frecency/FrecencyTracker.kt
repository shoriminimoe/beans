package com.example.beans.frecency

import android.content.SharedPreferences

/**
 * Tracks frecency (frequency + recency) for payees and accounts.
 * Persists data to SharedPreferences.
 *
 * Score formula: count * recencyBoost
 * recencyBoost = 1.0 / (1.0 + (now - lastUsed) / HALF_LIFE)
 */
class FrecencyTracker(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val payeeData: MutableMap<String, FrecencyEntry> = loadData(KEY_PAYEES)
    private val accountData: MutableMap<String, FrecencyEntry> = loadData(KEY_ACCOUNTS)

    data class FrecencyEntry(
        val count: Int,
        val lastUsed: Long,
    )

    fun recordPayee(payee: String) {
        record(payeeData, payee)
        saveData(KEY_PAYEES, payeeData)
    }

    fun recordAccount(account: String) {
        record(accountData, account)
        saveData(KEY_ACCOUNTS, accountData)
    }

    fun suggestPayees(prefix: String): List<String> = suggest(payeeData, prefix)

    fun suggestAccounts(prefix: String): List<String> = suggest(accountData, prefix)

    /**
     * Returns account completions incrementally by delimiter.
     * Given prefix "Expenses:", returns ["Expenses:Food", "Expenses:Transport"]
     * not the full deep paths like "Expenses:Food:Coffee".
     *
     * If prefix is empty, returns top-level segments (e.g. "Expenses", "Assets").
     * Results are sorted by the frecency of their best-matching full account.
     */
    fun completeAccountIncremental(prefix: String): List<String> {
        val depth = if (prefix.isEmpty()) 0 else prefix.count { it == ':' }
        val targetSegments = depth + 1

        val matchingAccounts =
            accountData.keys.filter { account ->
                if (prefix.isEmpty()) {
                    true
                } else {
                    account.startsWith(prefix)
                }
            }

        // Group by truncated-to-target-depth prefix, keep best score per group
        val groups = mutableMapOf<String, Double>()
        for (account in matchingAccounts) {
            val parts = account.split(":")
            if (parts.size < targetSegments) continue
            val truncated = parts.take(targetSegments).joinToString(":")
            val score = score(accountData[account]!!)
            groups[truncated] = maxOf(groups[truncated] ?: 0.0, score)
        }

        return groups.entries.sortedByDescending { it.value }.map { it.key }
    }

    private fun record(
        data: MutableMap<String, FrecencyEntry>,
        key: String,
    ) {
        val existing = data[key]
        val count = (existing?.count ?: 0) + 1
        data[key] = FrecencyEntry(count, clock())
    }

    private fun suggest(
        data: Map<String, FrecencyEntry>,
        prefix: String,
    ): List<String> =
        data.entries
            .filter { (key, _) ->
                if (prefix.isEmpty()) {
                    true
                } else {
                    key.startsWith(prefix, ignoreCase = true)
                }
            }.sortedByDescending { (_, entry) -> score(entry) }
            .map { it.key }

    private fun score(entry: FrecencyEntry): Double {
        val age = (clock() - entry.lastUsed).coerceAtLeast(0)
        val recencyBoost = 1.0 / (1.0 + age.toDouble() / HALF_LIFE)
        return entry.count * recencyBoost
    }

    /**
     * Serialization format: "key\tcount\tlastUsed\n" per entry.
     * Tab is used as delimiter since it won't appear in account/payee names.
     */
    private fun loadData(key: String): MutableMap<String, FrecencyEntry> {
        val raw = prefs.getString(key, null) ?: return mutableMapOf()
        val map = mutableMapOf<String, FrecencyEntry>()
        for (line in raw.split("\n")) {
            if (line.isBlank()) continue
            val parts = line.split("\t")
            if (parts.size == 3) {
                try {
                    map[parts[0]] = FrecencyEntry(parts[1].toInt(), parts[2].toLong())
                } catch (_: NumberFormatException) {
                    // skip corrupt entries
                }
            }
        }
        return map
    }

    private fun saveData(
        key: String,
        data: Map<String, FrecencyEntry>,
    ) {
        val sb = StringBuilder()
        for ((k, v) in data) {
            sb
                .append(k)
                .append('\t')
                .append(v.count)
                .append('\t')
                .append(v.lastUsed)
                .append('\n')
        }
        prefs.edit().putString(key, sb.toString()).apply()
    }

    companion object {
        private const val KEY_PAYEES = "frecency_payees"
        private const val KEY_ACCOUNTS = "frecency_accounts"
        private const val HALF_LIFE = 7 * 24 * 60 * 60 * 1000.0 // 7 days in ms
    }
}
