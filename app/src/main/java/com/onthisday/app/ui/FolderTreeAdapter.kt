package com.onthisday.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.onthisday.app.R
import com.onthisday.app.data.BucketInfo

/**
 * Builds a simple 2-level tree from bucket paths:
 *   /storage/emulated/0/DCIM/Camera  → parent=DCIM, child=Camera
 *   /storage/emulated/0/Pictures     → parent=Pictures (root-level)
 */
class FolderTreeAdapter(
    buckets: List<BucketInfo>,
    private val initiallySelected: Set<String>
) : RecyclerView.Adapter<FolderTreeAdapter.VH>() {

    data class Row(
        val displayName: String,
        val indent: Int,           // 0 = top-level, 1 = child
        val isHeader: Boolean,     // true = non-selectable parent label
        var checked: Boolean
    )

    private val rows: List<Row>
    // map display name → checked state for easy retrieval
    private val checkMap = mutableMapOf<String, Boolean>()

    init {
        // Group by parent directory segment
        // path example: /storage/emulated/0/DCIM/Camera
        // We use the second-to-last segment as the "parent" if depth > standard base
        val baseDepth = 4 // /storage/emulated/0 = 4 segments (indices 0-3 after split)

        data class Node(val display: String, val children: MutableList<String> = mutableListOf())

        val parents = linkedMapOf<String, Node>()

        buckets.forEach { bucket ->
            val parts = bucket.path.trimEnd('/').split('/')
            val depth = parts.size
            if (depth > baseDepth + 1) {
                // Has a meaningful parent segment
                val parent = parts[depth - 2]
                parents.getOrPut(parent) { Node(parent) }.children.add(bucket.displayName)
            } else {
                // Root-level bucket (e.g. /storage/emulated/0/Pictures)
                if (!parents.containsKey(bucket.displayName)) {
                    parents[bucket.displayName] = Node(bucket.displayName)
                }
            }
        }

        val built = mutableListOf<Row>()
        val allSelected = initiallySelected.isEmpty()

        parents.values.forEach { node ->
            if (node.children.isEmpty()) {
                // Leaf at root level — directly selectable
                val checked = allSelected || initiallySelected.contains(node.display)
                checkMap[node.display] = checked
                built.add(Row(node.display, 0, false, checked))
            } else {
                // Non-selectable parent header
                built.add(Row(node.display, 0, true, false))
                node.children.forEach { child ->
                    val checked = allSelected || initiallySelected.contains(child)
                    checkMap[child] = checked
                    built.add(Row(child, 1, false, checked))
                }
            }
        }

        rows = built
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName:   TextView = view.findViewById(R.id.tvFolderName)
        val checkbox: CheckBox = view.findViewById(R.id.cbFolder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder_row, parent, false)
        return VH(v)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.tvName.text = row.displayName

        // Indent
        val dp = holder.itemView.context.resources.displayMetrics.density
        holder.itemView.setPaddingRelative((16 + row.indent * 24) * dp.toInt(), 0, 16 * dp.toInt(), 0)

        if (row.isHeader) {
            holder.checkbox.visibility = View.GONE
            holder.tvName.alpha = 0.5f
        } else {
            holder.checkbox.visibility = View.VISIBLE
            holder.tvName.alpha = 1f
            holder.checkbox.isChecked = row.checked
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                rows[holder.adapterPosition].checked = isChecked
                checkMap[row.displayName] = isChecked
            }
            holder.itemView.setOnClickListener {
                holder.checkbox.toggle()
            }
        }
    }

    fun getSelectedFolders(): Set<String> =
        checkMap.entries.filter { it.value }.map { it.key }.toSet()
}
