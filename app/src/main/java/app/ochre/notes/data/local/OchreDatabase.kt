package app.ochre.notes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoEntity::class,
        AttachmentEntity::class,
        ReminderEntity::class,
        MemoTagEntity::class,
        MemoLinkEntity::class,
        MemoFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OchreDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun tagDao(): TagDao
    abstract fun linkDao(): LinkDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        fun build(context: Context): OchreDatabase =
            Room.databaseBuilder(context, OchreDatabase::class.java, "ochre.db")
                .addCallback(TriggerCallback)
                .build()

        private object TriggerCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                createTriggers(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                createTriggers(db)
            }

            private fun createTriggers(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memo_fts_after_insert
                    AFTER INSERT ON memos BEGIN
                        INSERT INTO memo_fts(rowid, content, title)
                        VALUES (new.id, new.content, new.title);
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memo_fts_after_update
                    AFTER UPDATE OF content, title ON memos BEGIN
                        UPDATE memo_fts SET content = new.content, title = new.title
                        WHERE rowid = new.id;
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memo_fts_after_delete
                    AFTER DELETE ON memos BEGIN
                        DELETE FROM memo_fts WHERE rowid = old.id;
                    END
                    """.trimIndent(),
                )
            }
        }
    }
}
