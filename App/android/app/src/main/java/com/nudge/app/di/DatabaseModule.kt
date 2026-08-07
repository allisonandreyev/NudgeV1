package com.nudge.app.di

import android.content.Context
import androidx.room.Room
import com.nudge.app.data.*
import com.nudge.app.utils.SecurityUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NudgeDatabase {
        val passphrase = SecurityUtils.getDatabasePassphrase(context)
        val factory = net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase)
        
        return Room.databaseBuilder(
            context,
            NudgeDatabase::class.java,
            "nudge_secure_v4.db"
        )
        .openHelperFactory(factory)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideDataPointDao(database: NudgeDatabase): DataPointDao {
        return database.dataPointDao()
    }

    @Provides
    fun providePhysicianConnectionDao(database: NudgeDatabase): PhysicianConnectionDao {
        return database.physicianConnectionDao()
    }

    @Provides
    fun provideUserStatsDao(database: NudgeDatabase): UserStatsDao {
        return database.userStatsDao()
    }

    @Provides
    fun provideUserDao(database: NudgeDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideTherapySessionDao(database: NudgeDatabase): TherapySessionDao {
        return database.therapySessionDao()
    }
}
