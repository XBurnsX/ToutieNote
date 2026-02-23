package com.toutieserver.toutienote.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toutieserver.toutienote.data.models.Note
import com.toutieserver.toutienote.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItem(
    note: Note,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
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
        Column {
            Text(
                text = note.title.ifEmpty { "Sans titre" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.content.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = note.content.replace("\n", " "),
                    fontSize = 12.sp,
                    color = MutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
