package app.ochre.notes.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromAttachmentKind(value: AttachmentKind): String = value.name

    @TypeConverter
    fun toAttachmentKind(value: String): AttachmentKind =
        runCatching { AttachmentKind.valueOf(value) }.getOrDefault(AttachmentKind.FILE)
}
