package org.milo.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.milo.agenda.data.CheckItem
import org.milo.agenda.data.Note
import org.milo.agenda.data.Store
import java.time.LocalDate

@Composable
fun NotesScreen(onBack: () -> Unit, initialId: Long?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 760.dp
        var selId by rememberSaveable { mutableLongStateOf(initialId ?: -1L) }
        var query by rememberSaveable { mutableStateOf("") }
        var filter by rememberSaveable { mutableIntStateOf(0) } // 0 todas · 1 listas · 2 de eventos
        val editingNarrow = !wide && selId >= 0L

        fun newNote() {
            val n = Note(Store.newId(), color = 0)
            Store.upsertNote(n)
            selId = n.id
            filter = 0; query = ""
        }

        Column(Modifier.fillMaxSize()) {
            TopBar("Notas", onBack = { if (editingNarrow) selId = -1L else onBack() }) {
                IconButton(onClick = { newNote() }) { Icon(Icons.Rounded.Add, "Nueva nota") }
            }
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    NoteList(query, { query = it }, filter, { filter = it }, selId, { selId = it },
                        Modifier.width(440.dp).fillMaxHeight())
                    val note = Store.note(selId)
                    if (note != null) NoteEditor(note, Modifier.weight(1f).fillMaxHeight(), onDeleted = { selId = -1L })
                    else EditorPlaceholder(Modifier.weight(1f).fillMaxHeight(), onNew = { newNote() })
                }
            } else {
                val note = Store.note(selId)
                if (editingNarrow && note != null) {
                    NoteEditor(note, Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp), onDeleted = { selId = -1L })
                } else {
                    NoteList(query, { query = it }, filter, { filter = it }, selId, { selId = it },
                        Modifier.fillMaxSize().padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun NoteList(
    query: String, onQuery: (String) -> Unit,
    filter: Int, onFilter: (Int) -> Unit,
    selId: Long, onSelect: (Long) -> Unit,
    modifier: Modifier,
) {
    val filtered = remember(Store.notes.toList(), query, filter) {
        Store.notes
            .filter { n ->
                when (filter) {
                    1 -> n.items.isNotEmpty()
                    2 -> n.eventId != null
                    else -> true
                }
            }
            .filter { n ->
                query.isBlank() || (n.title + " " + n.body + " " + n.items.joinToString(" ") { it.text })
                    .contains(query, ignoreCase = true)
            }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query, onValueChange = onQuery,
            placeholder = { Text("Buscar en notas") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Borrar") } },
            singleLine = true, shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todas", "Listas", "De eventos").forEachIndexed { i, label ->
                FilterChip(selected = filter == i, onClick = { onFilter(i) }, label = { Text(label) })
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "Sin notas todavía" else "Nada coincide", color = C.InkVar)
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered, key = { it.id }) { n -> NoteCard(n, selected = n.id == selId) { onSelect(n.id) } }
        }
    }
}

@Composable
private fun NoteCard(n: Note, selected: Boolean, onClick: () -> Unit) {
    val (bg, fg) = noteColors[n.color.coerceIn(0, noteColors.lastIndex)]
    val event = n.eventId?.let { Store.event(it) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg)
            .then(if (selected) Modifier.border(3.dp, C.Primary, RoundedCornerShape(20.dp)) else Modifier)
            .clickable { onClick() }.padding(16.dp),
    ) {
        if (event != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Rounded.Event, null, tint = fg, modifier = Modifier.size(14.dp))
            Text("${event.title} · ${fmtShortDay(event.date)} ${event.start.format(HM)}", fontSize = 11.sp, color = fg, fontWeight = FontWeight.Medium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(n.title.ifBlank { "Sin título" }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = fg,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.pinned) Icon(Icons.Rounded.PushPin, "Fijada", tint = fg, modifier = Modifier.size(18.dp))
        }
        if (n.items.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            n.items.take(4).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (item.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                        tint = fg, modifier = Modifier.size(18.dp))
                    Text(item.text, fontSize = 13.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null)
                }
            }
            if (n.items.size > 4) Text("+${n.items.size - 4} más", fontSize = 12.sp, color = fg, modifier = Modifier.padding(start = 24.dp, top = 2.dp))
        } else if (n.body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(n.body, fontSize = 13.sp, color = fg, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun EditorPlaceholder(modifier: Modifier, onNew: () -> Unit) {
    Tile(C.SurfaceC, modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Description, null, tint = C.Outline, modifier = Modifier.size(48.dp))
                Text("Elige una nota o crea una nueva", color = C.InkVar)
                Button(onClick = onNew) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Nueva nota") }
            }
        }
    }
}

@Composable
private fun NoteEditor(note: Note, modifier: Modifier, onDeleted: () -> Unit) {
    // El estado vive localmente y se persiste en cada cambio; se reinicia al cambiar de nota.
    var n by remember(note.id) { mutableStateOf(note) }
    fun update(next: Note) { n = next; Store.upsertNote(next) }
    val (bg, _) = noteColors[n.color.coerceIn(0, noteColors.lastIndex)]
    val event = n.eventId?.let { Store.event(it) }

    Tile(bg, modifier, RoundedCornerShape(28.dp), PaddingValues(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { update(n.copy(pinned = !n.pinned)) }) {
                Icon(Icons.Rounded.PushPin, if (n.pinned) "Quitar de fijadas" else "Fijar",
                    tint = if (n.pinned) C.Primary else C.Outline)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                noteColors.forEachIndexed { i, (dot, _) ->
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(dot)
                            .border(if (n.color == i) 3.dp else 1.dp, if (n.color == i) C.Primary else C.OutlineVar, CircleShape)
                            .clickable { update(n.copy(color = i)) },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { Store.deleteNote(n.id); onDeleted() }) { Icon(Icons.Rounded.Delete, "Eliminar nota", tint = C.Error) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (event != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Event, null, tint = C.Primary, modifier = Modifier.size(18.dp))
                    Text("${event.title} · ${fmtShortDay(event.date)} ${event.start.format(HM)}",
                        fontSize = 13.sp, color = C.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { update(n.copy(eventId = null)) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, "Desvincular", tint = C.Outline, modifier = Modifier.size(18.dp))
                    }
                }
            }
            BasicField(n.title, { update(n.copy(title = it)) }, "Título", 24.sp, FontWeight.Medium, singleLine = true)
            BasicField(n.body, { update(n.copy(body = it)) }, "Escribe algo…", 16.sp, FontWeight.Normal, singleLine = false)

            if (n.items.isNotEmpty()) Spacer(Modifier.height(4.dp))
            n.items.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = {
                        update(n.copy(items = n.items.toMutableList().also { it[i] = item.copy(done = !item.done) }))
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(if (item.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                            tint = if (item.done) C.Primary else C.Outline)
                    }
                    BasicField(item.text, { txt ->
                        update(n.copy(items = n.items.toMutableList().also { it[i] = item.copy(text = txt) }))
                    }, "Elemento", 16.sp, FontWeight.Normal, singleLine = true, modifier = Modifier.weight(1f),
                        strike = item.done)
                    IconButton(onClick = {
                        update(n.copy(items = n.items.toMutableList().also { it.removeAt(i) }))
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, "Quitar elemento", tint = C.Outline, modifier = Modifier.size(18.dp))
                    }
                }
            }
            TextButton(onClick = { update(n.copy(items = n.items + CheckItem(""))) }) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Añadir elemento")
            }
        }
        Text("Se guarda solo, en este dispositivo.", fontSize = 12.sp, color = C.Outline, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun BasicField(
    value: String, onValue: (String) -> Unit, placeholder: String,
    size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, singleLine: Boolean,
    modifier: Modifier = Modifier, strike: Boolean = false,
) {
    TextField(
        value = value, onValueChange = onValue,
        placeholder = { Text(placeholder, fontSize = size, color = C.Outline) },
        singleLine = singleLine,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = size, fontWeight = weight, color = C.Ink,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        ),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = C.Primary, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = C.Primary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
