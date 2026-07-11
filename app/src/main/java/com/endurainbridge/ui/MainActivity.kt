package com.endurainbridge.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.endurainbridge.R
import com.endurainbridge.databinding.ActivityMainBinding

/** Hosts the two tabs (Ajustes | Historial) via a BottomNavigationView. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // targetSdk 35 draws edge-to-edge: pad the root so the toolbar clears the status bar and the
        // bottom navigation clears the gesture/navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_settings -> show(SettingsFragment(), R.string.tab_settings)
                R.id.nav_history -> show(HistoryFragment(), R.string.tab_history)
                else -> return@setOnItemSelectedListener false
            }
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_settings
        }
    }

    private fun show(fragment: Fragment, titleRes: Int) {
        supportActionBar?.title = getString(titleRes)
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }
}
