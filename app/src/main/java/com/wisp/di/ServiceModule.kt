package com.wisp.di

import android.content.Context
import com.wisp.service.AppResolver
import com.wisp.service.DefaultDeviceController
import com.wisp.service.DeviceController
import com.wisp.service.GestureFactory
import com.wisp.service.ScreenSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI wiring for `com.wisp.service` (phase-03). Separate from [AppModule] so
 * parallel phases don't collide on one module file. `ScreenChangeSignals` is
 * constructor-injected (`@Inject`), so it needs no explicit provider here.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides
    @Singleton
    fun provideScreenSerializer(): ScreenSerializer = ScreenSerializer()

    @Provides
    @Singleton
    fun provideGestureFactory(): GestureFactory = GestureFactory()

    @Provides
    @Singleton
    fun provideAppResolver(): AppResolver = AppResolver()

    @Provides
    @Singleton
    fun provideDeviceController(
        @ApplicationContext context: Context,
        serializer: ScreenSerializer,
        gestureFactory: GestureFactory,
        appResolver: AppResolver,
    ): DeviceController = DefaultDeviceController(context, serializer, gestureFactory, appResolver)
}
