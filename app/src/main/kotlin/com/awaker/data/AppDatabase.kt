package com.awaker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SessionRecord::class, CheckpointEvent::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun checkpointDao(): CheckpointDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // v2 (이슈 05): checkpoint_events 추가 — 베타 데이터 보존 위해 파괴적 마이그레이션 금지.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS checkpoint_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sessionId TEXT NOT NULL, " +
                        "shownAtWall INTEGER NOT NULL, " +
                        "ordinal INTEGER NOT NULL, " +
                        "heightPct INTEGER NOT NULL, " +
                        "choice TEXT, " +
                        "leftWithinMinute INTEGER)",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "awaker.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
