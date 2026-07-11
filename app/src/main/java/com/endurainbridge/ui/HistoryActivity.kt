package com.endurainbridge.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.endurainbridge.R
import com.endurainbridge.data.Settings
import com.endurainbridge.data.UploadHistory
import com.endurainbridge.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var history: UploadHistory
    private lateinit var adapter: UploadHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.history_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        history = UploadHistory(this)
        adapter = UploadHistoryAdapter(emptyList(), ::openInEndurain)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = history.all()
        adapter.submit(items)
        binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openInEndurain(endurainId: Long) {
        val base = Settings.get(this).serverUrl
        if (base.isBlank()) {
            Toast.makeText(this, R.string.toast_configure_first, Toast.LENGTH_LONG).show()
            return
        }
        val url = "$base/activity/$endurainId"
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_refresh -> { refresh(); true }
        R.id.action_clear -> { history.clear(); refresh(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
