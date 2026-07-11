package com.endurainbridge.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.endurainbridge.R
import com.endurainbridge.data.UploadHistoryEntry
import com.endurainbridge.data.UploadStatus
import com.endurainbridge.databinding.ItemUploadHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UploadHistoryAdapter(
    private var items: List<UploadHistoryEntry>,
    private val onOpen: (Long) -> Unit,
) : RecyclerView.Adapter<UploadHistoryAdapter.VH>() {

    private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

    fun submit(newItems: List<UploadHistoryEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemUploadHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val b: ItemUploadHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: UploadHistoryEntry) {
            val ctx = b.root.context
            b.name.text = e.name.ifBlank { ctx.getString(R.string.app_name) }
            b.time.text = dateFormat.format(Date(e.timeMs))

            when (e.status) {
                UploadStatus.PENDING -> {
                    b.status.text = ctx.getString(R.string.history_status_pending)
                    b.status.setTextColor(Color.parseColor("#F9A825"))
                }
                UploadStatus.SUCCESS -> {
                    b.status.text = ctx.getString(R.string.history_status_success)
                    b.status.setTextColor(Color.parseColor("#2E7D32"))
                }
                UploadStatus.FAILED -> {
                    b.status.text = ctx.getString(R.string.history_status_failed, e.detail ?: "")
                    b.status.setTextColor(Color.parseColor("#C62828"))
                }
            }

            val id = e.endurainId
            if (e.status == UploadStatus.SUCCESS && id != null) {
                b.openLink.visibility = android.view.View.VISIBLE
                b.openLink.setOnClickListener { onOpen(id) }
            } else {
                b.openLink.visibility = android.view.View.GONE
                b.openLink.setOnClickListener(null)
            }
        }
    }
}
