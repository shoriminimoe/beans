package com.example.beans.parser

import com.example.beans.model.Amount
import com.example.beans.model.Posting
import com.example.beans.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate

sealed class BeancountEntry {
    abstract val lineNumber: Int
}

data class TransactionEntry(
    val transaction: Transaction,
    override val lineNumber: Int = 0
) : BeancountEntry()

data class RawDirective(
    val text: String,
    override val lineNumber: Int = 0
) : BeancountEntry()

data class BeancountFile(val entries: List<BeancountEntry>) {
    val transactions: List<Transaction>
        get() = entries.filterIsInstance<TransactionEntry>().map { it.transaction }
}

class BeancountParser {

    private val txnHeaderRegex = Regex(
        """^(\d{4}-\d{2}-\d{2})\s+([*!])\s+(?:"([^"]*)"\s+)?"([^"]*)"$"""
    )

    private val postingWithAmountRegex = Regex(
        """^\s{2,}(\S[\w:]+(?::\w[\w-]*)*)\s{2,}(-?[\d,]+\.?\d*)\s+([A-Z][A-Z0-9_-]*)$"""
    )

    private val postingNoAmountRegex = Regex(
        """^\s{2,}(\S[\w:]+(?::\w[\w-]*)*)$"""
    )

    fun parse(text: String): BeancountFile {
        if (text.isBlank()) return BeancountFile(emptyList())

        val lines = text.lines()
        val entries = mutableListOf<BeancountEntry>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val headerMatch = txnHeaderRegex.matchEntire(line.trim())

            if (headerMatch != null) {
                val (dateStr, flag, payeeOrEmpty, narration) = headerMatch.destructured
                val date = LocalDate.parse(dateStr)
                val payee = payeeOrEmpty
                val postings = mutableListOf<Posting>()
                i++

                while (i < lines.size) {
                    val postingLine = lines[i]
                    if (postingLine.isBlank()) break

                    val amountMatch = postingWithAmountRegex.matchEntire(postingLine)
                    val noAmountMatch = postingNoAmountRegex.matchEntire(postingLine)

                    when {
                        amountMatch != null -> {
                            val (account, value, currency) = amountMatch.destructured
                            val cleanValue = value.replace(",", "")
                            postings.add(Posting(account, Amount(BigDecimal(cleanValue), currency)))
                            i++
                        }
                        noAmountMatch != null -> {
                            postings.add(Posting(noAmountMatch.groupValues[1], null))
                            i++
                        }
                        else -> break
                    }
                }

                entries.add(
                    TransactionEntry(
                        Transaction(date, flag, payee, narration, postings),
                        lineNumber = i
                    )
                )
            } else if (line.isNotBlank()) {
                entries.add(RawDirective(line, lineNumber = i))
                i++
            } else {
                i++
            }
        }

        return BeancountFile(entries)
    }

    fun serialize(file: BeancountFile): String {
        val sb = StringBuilder()

        for ((index, entry) in file.entries.withIndex()) {
            when (entry) {
                is RawDirective -> sb.appendLine(entry.text)
                is TransactionEntry -> {
                    val txn = entry.transaction
                    val header = buildString {
                        append(txn.date)
                        append(" ")
                        append(txn.flag)
                        append(" ")
                        if (txn.payee.isNotEmpty()) {
                            append("\"${txn.payee}\" ")
                        }
                        append("\"${txn.narration}\"")
                    }
                    sb.appendLine(header)

                    for (posting in txn.postings) {
                        if (posting.amount != null) {
                            sb.appendLine("  ${posting.account}  ${posting.amount.value.toPlainString()} ${posting.amount.currency}")
                        } else {
                            sb.appendLine("  ${posting.account}")
                        }
                    }
                }
            }

            // Add blank line between entries
            if (index < file.entries.size - 1) {
                val nextEntry = file.entries[index + 1]
                if (nextEntry is TransactionEntry || entry is TransactionEntry) {
                    sb.appendLine()
                }
            }
        }

        return sb.toString().trimEnd() + "\n"
    }
}
