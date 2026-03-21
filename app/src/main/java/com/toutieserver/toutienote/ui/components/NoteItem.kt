package com.toutieserver.toutienote.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.ui.theme.*
import com.toutieserver.toutienote.util.formatNoteDate

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItem(
    note: Note,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val previewText = remember(note.content) {
        note.content
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("\n", " ")
            .trim()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Surface2Color else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) BorderColor else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.isLocked) {
                        Icon(Icons.Default.Lock, null, tint = MutedColor, modifier = Modifier.size(14.dp))
                    }
                    Text(
                        text = note.title.ifEmpty { "Sans titre" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (note.isPinned) {
                        Icon(Icons.Default.PushPin, null, tint = LocalAccentColor.current, modifier = Modifier.size(13.dp))
                    }
                    if (note.isFavorite) {
                        Icon(Icons.Default.Star, null, tint = LocalAccentColor.current, modifier = Modifier.size(13.dp))
                    }
                    Text(
                        text = formatNoteDate(note.updatedAt),
                        fontSize = 11.sp,
                        color = MutedColor,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.MoreVert, "Menu note", tint = MutedColor, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (note.isFavorite) "Retirer des favoris" else "Ajouter aux favoris") },
                                onClick = { onToggleFavorite(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (note.isPinned) "Desepingler" else "Epingler") },
                                onClick = { onTogglePin(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (note.isLocked) "Deverrouiller" else "Verrouiller") },
                                onClick = { onToggleLock(); showMenu = false }
                            )
                            HorizontalDivider(color = BorderColor)
                            DropdownMenuItem(
                                text = { Text("Supprimer", color = DangerColor) },
                                onClick = { onDelete(); showMenu = false }
                            )
                        }
                    }
                }
            }
            if (note.isLocked) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Contenu verrouille",
                    fontSize = 12.sp,
                    color = MutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (previewText.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = previewText,
                    fontSize = 12.sp,
                    color = MutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.tags.take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(LocalAccentColor.current.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                color = LocalAccentColor.current,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
