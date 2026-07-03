package com.wisp.di

import android.content.Context
import androidx.room.Room
import com.wisp.data.WispDatabase
import com.wisp.data.ContextTracker
import com.wisp.data.CostCalculator
import com.wisp.data.PrefsSettingsStore
import com.wisp.data.ScreenshotStore
import com.wisp.data.SessionRepository
import com.wisp.data.SettingsStore
import com.wisp.data.TaskMemoryRepository
import com.wisp.data.TaskRecipeDao
import com.wisp.memory.MemoryStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for phase-05 (session DB + context management) plus phase-12
 * additions (settings, task-recipe index). Kept separate from `AppModule` so
 * parallel phases don't collide on one file.
 *
 * Migration (phase-12): a real numbered `MIGRATION_1_2` — no destructive
 * fallback, so the user's session history survives the schema bump.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): WispDatabase =
        Room
            .databaseBuilder(context, WispDatabase::class.java, WispDatabase.NAME)
            .addMigrations(WispDatabase.MIGRATION_1_2)
            .build()

    @Provides
    @Singleton
    fun provideScreenshotStore(
        @ApplicationContext context: Context,
    ): ScreenshotStore = ScreenshotStore(context.filesDir)

    @Provides
    @Singleton
    fun provideCostCalculator(): CostCalculator = CostCalculator()

    @Provides
    @Singleton
    fun provideSessionRepository(
        db: WispDatabase,
        screenshotStore: ScreenshotStore,
        costCalculator: CostCalculator,
    ): SessionRepository = SessionRepository(db, screenshotStore, costCalculator)

    @Provides
    @Singleton
    fun provideContextTracker(
        db: WispDatabase,
        costCalculator: CostCalculator,
    ): ContextTracker = ContextTracker(db, costCalculator)

    @Provides
    @Singleton
    fun provideSettingsStore(
        @ApplicationContext context: Context,
    ): SettingsStore = PrefsSettingsStore(context)

    @Provides
    fun provideTaskRecipeDao(db: WispDatabase): TaskRecipeDao = db.taskRecipeDao()

    @Provides
    @Singleton
    fun provideTaskMemoryRepository(
        taskRecipeDao: TaskRecipeDao,
        memoryStore: MemoryStore,
    ): TaskMemoryRepository = TaskMemoryRepository(taskRecipeDao, memoryStore)
}
