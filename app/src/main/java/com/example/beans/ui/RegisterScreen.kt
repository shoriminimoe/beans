package com.example.beans.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.example.beans.model.RegisterEntry
import java.time.format.DateTimeFormatter

/**
 * Shared, immutable date formatter. Hoisted out of [RegisterRow] so a new one
 * is not parsed and built for every row that scrolls into view —
 * [DateTimeFormatter] is thread-safe and stateless, so a single instance
 * serves the whole register.
 */
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    accounts: List<String>,
    selectedAccount: String?,
    entries: List<RegisterEntry>,
    onAccountSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Register") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            AccountPicker(
                accounts = accounts,
                selectedAccount = selectedAccount,
                onAccountSelect = onAccountSelect,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            )

            when {
                selectedAccount == null -> RegisterMessage("Select an account")
                entries.isEmpty() -> RegisterMessage("No transactions for this account")
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Accounts the register picker offers for [query]: a case-insensitive
 * substring match over [accounts], with accounts that start with the query
 * ranked first. A blank query offers the whole list. The input order of
 * [accounts] (already sorted) is preserved within each rank.
 */
internal fun filterAccounts(
    accounts: List<String>,
    query: String,
): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return accounts
    return accounts
        .filter { it.contains(q, ignoreCase = true) }
        .sortedByDescending { it.startsWith(q, ignoreCase = true) }
}

/**
 * Account selector for the register. The user types to filter accounts and
 * picks one from the suggestion dropdown; only picking a suggestion selects
 * an account and drives [onAccountSelect]. The trailing arrow opens the full
 * list without typing.
 */
@Composable
private fun AccountPicker(
    accounts: List<String>,
    selectedAccount: String?,
    onAccountSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Field text is local — it changes as the user types, while
    // `selectedAccount` only changes once a suggestion is committed. Keying
    // the state on `selectedAccount` re-syncs the field when the selection
    // changes (including the initial selection and a ledger switch).
    var fieldValue by remember(selectedAccount) {
        mutableStateOf(textFieldValueAtEnd(selectedAccount ?: ""))
    }
    val matches =
        remember(fieldValue.text, accounts) {
            filterAccounts(accounts, fieldValue.text)
        }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                expanded = true
            },
            label = { Text("Account") },
            placeholder = { Text("Type to filter accounts") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Show accounts")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false },
            // Non-focusable so the text field keeps focus and the keyboard
            // stays up while suggestions narrow.
            properties = PopupProperties(focusable = false),
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            matches.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account) },
                    onClick = {
                        fieldValue = textFieldValueAtEnd(account)
                        expanded = false
                        onAccountSelect(account)
                    },
                )
            }
        }
    }
}

@Composable
private fun RegisterRow(entry: RegisterEntry) {
    val txn = entry.transaction

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            val headerText =
                buildAnnotatedString {
                    append(txn.date.format(dateFormatter))
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
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.change.isEmpty()) {
                        Text("—", style = MaterialTheme.typography.bodySmall)
                    } else {
                        for ((currency, amount) in entry.change) {
                            Text(
                                text = "${amount.toPlainString()} $currency",
                                style = MaterialTheme.typography.bodySmall,
                                color = balanceColor(amount),
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.balance.isEmpty()) {
                        Text("—", style = MaterialTheme.typography.bodySmall)
                    } else {
                        for ((currency, amount) in entry.balance) {
                            Text(
                                text = "${amount.toPlainString()} $currency",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = balanceColor(amount),
                            )
                        }
                    }
                }
            }
        }
    }
}
