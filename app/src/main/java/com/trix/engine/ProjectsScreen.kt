package com.trix.engine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Row(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrixLogo(130.dp)
        Spacer(Modifier.width(28.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("Trix Engine", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Para guardar seus jogos, a Trix Engine precisa de acesso ao armazenamento. " +
                        "Ela vai criar a pasta TrixEngine/Projects no seu celular, onde ficam todos os projetos " +
                        "(assim você também edita os arquivos pelo Termux).",
                color = Color(0xFFCBD5E1)
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRequest) { Text("Permitir acesso ao armazenamento") }
        }
    }
}

private val TEMPLATES = listOf("Vazio", "Coletar moedas", "Pulo com gravidade")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(onOpen: (File) -> Unit) {
    var projects by remember { mutableStateOf(Projects.list()) }
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TrixLogo(44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Trix Engine", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Seus projetos • v0.2", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }
            if (projects.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum projeto ainda.\nToque em \"Novo projeto\" para começar!",
                        textAlign = TextAlign.Center, color = Color(0xFF94A3B8)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(projects, key = { it.path }) { dir ->
                        val s = remember(dir.path, dir.lastModified()) { Projects.loadSettings(dir) }
                        Card(
                            onClick = { onOpen(dir) },
                            colors = CardDefaults.cardColors(containerColor = Surf),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(dir.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(
                                        (if (s.landscape) "Horizontal" else "Vertical") + " • ${s.worldW}×${s.worldH}",
                                        fontSize = 12.sp, color = Cyan
                                    )
                                    Text("Editado em " + fmt.format(Date(dir.lastModified())), fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                TextButton(onClick = { Projects.duplicate(dir); projects = Projects.list() }) { Text("Duplicar") }
                                IconButton(onClick = { toDelete = dir }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Apagar", tint = Color(0xFFF43F5E))
                                }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showNew = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Novo projeto") },
            containerColor = Cyan, contentColor = Color.Black,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        var template by remember { mutableIntStateOf(1) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("Novo projeto") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; error = null },
                        label = { Text("Nome") }, singleLine = true, isError = error != null,
                        supportingText = { error?.let { Text(it) } }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Modelo", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TEMPLATES.forEachIndexed { i, t ->
                            FilterChip(selected = template == i, onClick = { template = i }, label = { Text(t) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dir = Projects.create(name, template)
                    if (dir == null) error = "Nome inválido ou já existe"
                    else { projects = Projects.list(); showNew = false; onOpen(dir) }
                }) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancelar") } }
        )
    }

    toDelete?.let { dir ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Apagar \"${dir.name}\"?") },
            text = { Text("A pasta do projeto e todos os arquivos dela serão apagados.") },
            confirmButton = {
                TextButton(onClick = { dir.deleteRecursively(); projects = Projects.list(); toDelete = null }) {
                    Text("Apagar", color = Color(0xFFF43F5E))
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}
