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
                    if (entry.balance.isEmpty()) {
                        Text("—", style = MaterialTheme.typography.bodySmall)
                    } else {
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
}
