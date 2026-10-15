package com.lavidanoesunbanano.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lavidanoesunbanano.app.data.local.dao.TemplateMessageDao
import com.lavidanoesunbanano.app.data.local.entity.TemplateMessageEntity

@Database(
    entities = [TemplateMessageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LaVidaDatabase : RoomDatabase() {
    abstract fun templateMessageDao(): TemplateMessageDao
}
