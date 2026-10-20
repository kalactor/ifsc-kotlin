package dev.ifsc.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.ifsc.Ifsc
import dev.ifsc.offline.Branch
import dev.ifsc.offline.IfscDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                IfscScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IfscScreen() {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf<Branch?>(null) }
    var bankName by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Enter an IFSC code to look up a branch") }
    var database by remember { mutableStateOf<IfscDatabase?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        database = withContext(Dispatchers.IO) {
            IfscDatabase.open(context.applicationContext)
        }
        loading = false
    }

    // The database is intentionally kept open for the lifetime of the process.
    // Closing it from a composition effect can race with an in-flight lookup
    // when the activity is recreated or backgrounded.

    LaunchedEffect(query, database) {
        val code = query.trim().uppercase()
        branch = null
        bankName = null

        if (code.isEmpty()) {
            message = "Enter an IFSC code to look up a branch"
            return@LaunchedEffect
        }
        if (code.length < 11) {
            message = "${code.length} of 11 characters"
            return@LaunchedEffect
        }

        delay(250)
        val db = database ?: return@LaunchedEffect
        val found = withContext(Dispatchers.IO) { db.lookup(code) }
        branch = found
        bankName = withContext(Dispatchers.IO) { Ifsc.bankName(code) }
        message = when {
            found != null -> "Valid IFSC code"
            Ifsc.validate(code) -> "Valid IFSC code, but no branch details were found"
            else -> "Invalid IFSC code"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("IFSC Kotlin") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = query,
                onValueChange = { value ->
                    query = value.uppercase().filter { it.isLetterOrDigit() }.take(11)
                },
                label = { Text("IFSC code") },
                placeholder = { Text("e.g. HDFC0000001") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search,
                ),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                isError = query.length == 11 && message.startsWith("Invalid"),
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("HDFC0000001", "KKBK0000261", "PUNB0244200", "HDFC0CKUB01")
                    .forEach { example ->
                        AssistChip(
                            onClick = { query = example },
                            label = { Text(example) },
                        )
                    }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            branch != null -> MaterialTheme.colorScheme.primary
                            query.length == 11 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    val name = bankName ?: branch?.bank
                    if (name != null) {
                        Text(text = name, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            branch?.let { BranchCard(it) }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Offline data ${Ifsc.dataVersion} • ${Ifsc.branchCount} IFSC codes • no network permission",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BranchCard(branch: Branch) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(branch.bank, style = MaterialTheme.typography.titleLarge)
            branch.branch?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            branch.address?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DetailRow("IFSC", branch.ifsc, monospace = true)
            DetailRow("MICR", branch.micr)
            DetailRow("City", branch.city)
            DetailRow("District", branch.district)
            DetailRow("State", branch.state)
            DetailRow("SWIFT", branch.swift)
            DetailRow("Contact", branch.contact)

            val flags = listOfNotNull(
                "UPI".takeIf { branch.upi },
                "RTGS".takeIf { branch.rtgs },
                "NEFT".takeIf { branch.neft },
                "IMPS".takeIf { branch.imps },
            )
            if (flags.isNotEmpty()) {
                Text(
                    text = flags.joinToString("  •  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?, monospace: Boolean = false) {
    if (value.isNullOrBlank()) return
    Row {
        Text(
            text = label,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
        )
    }
}
