package com.trix.engine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TrixLogo(110.dp)
        Spacer(Modifier.height(16.dp))
        Text("Trix Engine", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text(
            "Para guardar seus jogos, a Trix Engine precisa de acesso ao armazenamento. " +
                    "Ela vai criar a pasta TrixEngine/Projects no seu celular, onde ficam todos os projetos " +
                    "(assim você também edita os arquivos pelo Termux).",
            textAlign = TextAlign.Center, color = Color(0xFFCBD5E1)
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Permitir acesso ao armazenamento") }
    }
}

@Composable
fun ProjectsScreen(onOpen: (File) -> Unit) {
    var projects by remember { mutableStateOf(Projects.list()) }
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TrixLogo(48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Trix Engine", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Seus projetos • v0.1", fontSize = 12.sp, color = Color(0xFF94A3B8))
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
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(projects, key = { it.path }) { dir ->
                        Card(
                            onClick = { onOpen(dir) },
                            colors = CardDefaults.cardColors(containerColor = Surf),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(dir.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Editado em " + fmt.format(Date(dir.lastModified())),
                                        fontSize = 12.sp, color = Color(0xFF94A3B8)
                                    )
                                }
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        )
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        var sample by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("Novo projeto") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; error = null },
                        label = { Text("Nome") }, singleLine = true, isError = error != null,
                        supportingText = { error?.let { Text(it) } }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = sample, onCheckedChange = { sample = it })
                        Spacer(Modifier.width(10.dp))
                        Text("Incluir jogo de exemplo")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dir = Projects.create(name, sample)
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
