package com.awaker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.awaker.data.AppDatabase
import com.awaker.data.SessionRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SessionRepository(AppDatabase.get(this).sessionDao())
        setContent {
            MaterialTheme {
                Surface { HomeScreen(repository) }
            }
        }
    }
}
