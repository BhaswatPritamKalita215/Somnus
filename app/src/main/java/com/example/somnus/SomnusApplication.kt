package com.example.somnus

import android.app.Application
import com.example.somnus.di.AppContainer
import com.example.somnus.di.DefaultAppContainer

/**
 * Somnus application class initializing the dependency container.
 */
class SomnusApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
