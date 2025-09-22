package net.coljac.tiratnavandana.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.coljac.tiratnavandana.data.ChantRepository
import net.coljac.tiratnavandana.model.Chant
import net.coljac.tiratnavandana.player.PlayerController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ChantRepository(context) }
    var chants by remember { mutableStateOf<List<Chant>>(emptyList()) }
    var currentChant by remember { mutableStateOf<Chant?>(null) }
    var selection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val playerController = remember { PlayerController(context, repo) }

    LaunchedEffect(Unit) {
        chants = repo.loadChants()
        currentChant = chants.firstOrNull()
        currentChant?.let { playerController.setChant(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Tiratnavandana") })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Chant selector
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = true }) {
                    Text(currentChant?.title ?: "Select chant")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    chants.forEach { chant ->
                        DropdownMenuItem(
                            text = { Text(chant.title) },
                            onClick = {
                                expanded = false
                                currentChant = chant
                                selection = emptySet()
                                playerController.setChant(chant)
                            }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                var loop by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Loop")
                    Switch(checked = loop, onCheckedChange = {
                        loop = it
                        playerController.setLoop(it)
                    })
                }
            }

            Divider()

            // Controls row
            val verses = currentChant?.verses ?: emptyList()
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    selection = if (selection.size == verses.size) emptySet() else verses.indices.toSet()
                    playerController.setSelection(selection.toList())
                }) { Text(if (selection.size == verses.size) "Clear All" else "Select All") }

                val playing = playerController.isPlaying()
                Button(onClick = {
                    if (playerController.isPlaying()) {
                        playerController.pause()
                    } else {
                        // If nothing selected, select all verses by default
                        if (selection.isEmpty() && verses.isNotEmpty()) {
                            selection = verses.indices.toSet()
                            playerController.setSelection(selection.toList())
                        }
                        playerController.play()
                    }
                }) { Text(if (playing) "Pause" else "Play") }
            }

            // Two columns filled top-to-bottom by column (1..n/2 in left, rest in right)
            val half = (verses.size + 1) / 2
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                val colScroll = rememberScrollState()
                val col2Scroll = rememberScrollState()
                Column(Modifier.weight(1f).verticalScroll(colScroll)) {
                    for (index in 0 until half) {
                        val verse = verses[index]
                        val checked = selection.contains(index)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selection = (selection + index).toSortedSet()
                                    playerController.setSelection(selection.toList())
                                    playerController.jumpToVerse(index)
                                }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selection = if (it) selection + index else selection - index
                                    playerController.setSelection(selection.toList())
                                },
                                modifier = Modifier.scale(0.85f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(verse.title.ifBlank { "Verse ${index + 1}" })
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).verticalScroll(col2Scroll)) {
                    for (index in half until verses.size) {
                        val verse = verses[index]
                        val checked = selection.contains(index)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selection = (selection + index).toSortedSet()
                                    playerController.setSelection(selection.toList())
                                    playerController.jumpToVerse(index)
                                }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selection = if (it) selection + index else selection - index
                                    playerController.setSelection(selection.toList())
                                },
                                modifier = Modifier.scale(0.85f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(verse.title.ifBlank { "Verse ${index + 1}" })
                        }
                    }
                }
            }

            // Scrolling text display synced to current verse progress
            val player = playerController.getPlayer()
            val currentIdx by playerController.currentSelectionIndex.collectAsState()
            val sortedSelection = remember(selection) { selection.toList().sorted() }
            val prog by produceState(initialValue = 0f, key1 = player, key2 = currentIdx) {
                while (true) {
                    val d = player.duration.takeIf { it > 0 } ?: 1L
                    val p = player.currentPosition
                    value = (p.toFloat() / d).coerceIn(0f, 1f)
                    kotlinx.coroutines.delay(60)
                }
            }
            ScrollingVerse(
                verses = verses.map { it.text },
                currentVerse = sortedSelection.getOrNull(currentIdx) ?: -1,
                progress = prog,
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(8.dp)
            )
        }
    }
}

@Composable
private fun ScrollingVerse(
    verses: List<String>,
    currentVerse: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    if (verses.isEmpty() || currentVerse !in verses.indices) return
    val currentText = verses[currentVerse]
    val prev = verses.getOrNull(currentVerse - 1)
    val next = verses.getOrNull(currentVerse + 1)
    val lines = remember(currentText) { currentText.split("\n") }
    val target = ((progress * (lines.size).coerceAtLeast(1)).toInt().coerceIn(0, (lines.size - 1).coerceAtLeast(0)))

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.Center) {
        if (prev != null) {
            Text(
                prev,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
        }
        // Current verse placed centrally
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            lines.forEachIndexed { i, line ->
                val isTarget = i == target
                Text(
                    line,
                    style = if (isTarget) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (next != null) {
            Text(
                next,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        }
    }
}
