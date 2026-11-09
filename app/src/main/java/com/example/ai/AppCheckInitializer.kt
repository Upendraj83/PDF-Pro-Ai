package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys defined by the Firebase SDK and runtime harness
private const val DEBUG_TOKEN_EXTRA = "app_check_debug_token"
private const val LEGACY_DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Volatile
private var isProviderInstalled = false

@Volatile
private var lastConfiguredDebugToken: String? = null

/**
 * Configures Firebase and Firebase App Check BEFORE any Firebase AI Logic model is accessed.
 * Uses synchronous .commit() when writing the debug secret in debug builds to avoid race conditions.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val appContext = context.applicationContext ?: context
        // 1. Ensure FirebaseApp is initialized
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        if (BuildConfig.DEBUG) {
            // 2. Debug: Read debug token from Intent extra (or system/env fallback)
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.takeIf { it.isNotBlank() }
                ?: intent?.getStringExtra(LEGACY_DEBUG_TOKEN_EXTRA)?.takeIf { it.isNotBlank() }
                ?: System.getProperty(DEBUG_TOKEN_EXTRA)?.takeIf { it.isNotBlank() }
                ?: System.getProperty(LEGACY_DEBUG_TOKEN_EXTRA)?.takeIf { it.isNotBlank() }
                ?: System.getenv(LEGACY_DEBUG_TOKEN_EXTRA)?.takeIf { it.isNotBlank() }

            var tokenUpdated = false
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = appContext.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                val existingStored = prefs.getString(DEBUG_SECRET_KEY, null)
                if (debugToken != lastConfiguredDebugToken || existingStored != debugToken) {
                    // CRITICAL: MUST use commit() instead of apply() so the secret is written
                    // synchronously before DebugAppCheckProviderFactory reads it.
                    prefs.edit()
                        .putString(DEBUG_SECRET_KEY, debugToken)
                        .commit()
                    lastConfiguredDebugToken = debugToken
                    tokenUpdated = true
                    Log.d(TAG, "App Check debug secret configured successfully.")
                }
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            if (!isProviderInstalled || tokenUpdated) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                isProviderInstalled = true
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            }
        } else {
            if (!isProviderInstalled) {
                // 3. Release: Look up recaptcha_site_key safely without throwing Resources.NotFoundException
                val resId = appContext.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", appContext.packageName)
                if (resId != 0) {
                    val siteKey = appContext.getString(resId)
                    if (siteKey.isNotBlank()) {
                        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                            RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                        )
                        isProviderInstalled = true
                        Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                    } else {
                        Log.e(TAG, "recaptcha_site_key resource string is blank.")
                    }
                } else {
                    Log.e(TAG, "recaptcha_site_key string resource not found in package.")
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

