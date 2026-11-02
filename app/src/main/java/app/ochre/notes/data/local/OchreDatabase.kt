package app.ochre.notes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoEntity::class,
        AttachmentEntity::class,
        ReminderEntity::class,
        MemoTagEntity::class,
        MemoLinkEntity::class,
        MemoFtsEntity::class,
        NotebookEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OchreDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun tagDao(): TagDao
    abstract fun linkDao(): LinkDao
    abstract fun reminderDao(): ReminderDao
    abstract fun notebookDao(): NotebookDao

    companion object {
        fun build(context: Context): OchreDatabase =
            Room.databaseBuilder(context, OchreDatabase::class.java, "ochre.db")
                .addCallback(TriggerCallback)
                .addMigrations(MIGRATION_1_2)
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notebooks` (
                        `uid` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        PRIMARY KEY(`uid`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE `memos` ADD COLUMN `notebookUid` TEXT")
                db.execSQL("ALTER TABLE `memos` ADD COLUMN `accentColor` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memos_notebookUid` ON `memos` (`notebookUid`)")
            }
        }

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
