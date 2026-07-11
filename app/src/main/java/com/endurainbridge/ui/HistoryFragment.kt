package com.endurainbridge.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.endurainbridge.R
import com.endurainbridge.data.Settings
import com.endurainbridge.data.UploadHistory
import com.endurainbridge.databinding.FragmentHistoryBinding

class HistoryFragment : Fragment() {

    private var _b: FragmentHistoryBinding? = null
    private val b get() = _b!!
    private lateinit var history: UploadHistory
    private lateinit var adapter: UploadHistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentHistoryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        history = UploadHistory(requireContext())
        adapter = UploadHistoryAdapter(emptyList(), ::openInEndurain)
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        b.btnClear.setOnClickListener {
            history.clear()
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun refresh() {
        val items = history.all()
        adapter.submit(items)
        b.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openInEndurain(endurainId: Long) {
        val base = Settings.get(requireContext()).serverUrl
        if (base.isBlank()) {
            Toast.makeText(requireContext(), R.string.toast_configure_first, Toast.LENGTH_LONG).show()
            return
        }
        val url = "$base/activity/$endurainId"
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(requireContext(), url, Toast.LENGTH_LONG).show() }
    }
}
