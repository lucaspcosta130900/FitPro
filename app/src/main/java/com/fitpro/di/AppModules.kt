package com.fitpro.di

import android.content.Context
import androidx.room.Room
import com.fitpro.data.local.AppDatabase
import com.fitpro.data.local.dao.*
import com.fitpro.data.local.dao.CardapioDao
import com.fitpro.data.local.dao.ShoppingDao
import com.fitpro.data.remote.AnthropicApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides fun provideFoodItemDao(db: AppDatabase): FoodItemDao = db.foodItemDao()
    @Provides fun provideMealEntryDao(db: AppDatabase): MealEntryDao = db.mealEntryDao()
    @Provides fun provideTrainingDao(db: AppDatabase): TrainingSessionDao = db.trainingSessionDao()
    @Provides fun provideBodyMetricDao(db: AppDatabase): BodyMetricDao = db.bodyMetricDao()
    @Provides fun provideLabExamDao(db: AppDatabase): LabExamDao = db.labExamDao()
    @Provides fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides fun provideUserGoalDao(db: AppDatabase): UserGoalDao = db.userGoalDao()
    @Provides fun provideCardapioDao(db: AppDatabase): CardapioDao = db.cardapioDao()
    @Provides fun provideShoppingDao(db: AppDatabase): ShoppingDao = db.shoppingDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(com.fitpro.data.remote.AnthropicHeaderInterceptor())
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.anthropic.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideAnthropicApiService(retrofit: Retrofit): AnthropicApiService =
        retrofit.create(AnthropicApiService::class.java)
}
