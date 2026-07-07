package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.PetRepository
import com.example.ui.MainAppScreen
import com.example.ui.PetViewModel
import com.example.ui.PetViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Room Database, DAO and Repository
        val database = AppDatabase.getDatabase(this)
        val repository = PetRepository(database.petDao())
        
        // Instantiate PetViewModel using factory
        val viewModelFactory = PetViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[PetViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}
