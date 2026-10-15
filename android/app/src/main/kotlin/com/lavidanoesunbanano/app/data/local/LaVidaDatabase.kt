package com.lavidanoesunbanano.app.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lavidanoesunbanano.app.data.local.dao.AttemptDao
import com.lavidanoesunbanano.app.data.local.dao.ExcuseDao
import com.lavidanoesunbanano.app.data.local.dao.ProfileDao
import com.lavidanoesunbanano.app.data.local.dao.RulesDao
import com.lavidanoesunbanano.app.data.local.dao.TemplateDao
import com.lavidanoesunbanano.app.data.local.entity.AttemptEntity
import com.lavidanoesunbanano.app.data.local.entity.BlockRangeEntity
import com.lavidanoesunbanano.app.data.local.entity.BlockedAppEntity
import com.lavidanoesunbanano.app.data.local.entity.ExcuseEntity
import com.lavidanoesunbanano.app.data.local.entity.ProfileEntity
import com.lavidanoesunbanano.app.data.local.entity.TemplateMessageEntity
import com.lavidanoesunbanano.app.data.local.entity.TemplateMetaEntity
import com.lavidanoesunbanano.app.data.local.entity.VerdictLineEntity

@Database(
    entities = [
        ProfileEntity::class,
        BlockedAppEntity::class,
        BlockRangeEntity::class,
        TemplateMessageEntity::class,
        VerdictLineEntity::class,
        TemplateMetaEntity::class,
        AttemptEntity::class,
        ExcuseEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // Cada versión solo agrega una tabla: el perfil y la plantilla del teléfono se conservan.
    autoMigrations = [AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
@TypeConverters(Converters::class)
abstract class LaVidaDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun rulesDao(): RulesDao
    abstract fun templateDao(): TemplateDao
    abstract fun attemptDao(): AttemptDao
    abstract fun excuseDao(): ExcuseDao
}
