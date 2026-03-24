package com.onthisday.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.onthisday.app.R
import com.onthisday.app.data.GalleryItem
import com.onthisday.app.data.Photo

class GalleryAdapter(
    private val onPhotoClick:     (Photo, View) -> Unit,
    private val onPhotoLongClick: (Photo, View) -> Unit
) : ListAdapter<GalleryItem, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        const val VIEW_HEADER = 0
        const val VIEW_PHOTO  = 1

        private val DIFF = object : DiffUtil.ItemCallback<GalleryItem>() {
            override fun areItemsTheSame(a: GalleryItem, b: GalleryItem): Boolean {
                if (a is GalleryItem.Header    && b is GalleryItem.Header)    return a.year     == b.year
                if (a is GalleryItem.PhotoItem && b is GalleryItem.PhotoItem) return a.photo.id == b.photo.id
                return false
            }
            override fun areContentsTheSame(a: GalleryItem, b: GalleryItem) = a == b
        }
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is GalleryItem.Header    -> VIEW_HEADER
        is GalleryItem.PhotoItem -> VIEW_PHOTO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_HEADER -> HeaderVH(inflater.inflate(R.layout.item_header, parent, false))
            else        -> PhotoVH(inflater.inflate(R.layout.item_photo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GalleryItem.Header    -> (holder as HeaderVH).bind(item)
            is GalleryItem.PhotoItem -> (holder as PhotoVH).bind(item.photo, onPhotoClick, onPhotoLongClick)
        }
    }

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvYear:  TextView = view.findViewById(R.id.tvYear)
        private val tvCount: TextView = view.findViewById(R.id.tvCount)
        fun bind(header: GalleryItem.Header) {
            tvYear.text  = header.year.toString()
            tvCount.text = itemView.context.resources.getQuantityString(
                R.plurals.photo_count, header.count, header.count)
        }
    }

    class PhotoVH(view: View) : RecyclerView.ViewHolder(view) {
        private val image:       ImageView = view.findViewById(R.id.imgPhoto)
        private val playOverlay: ImageView = view.findViewById(R.id.imgPlayOverlay)

        fun bind(photo: Photo, onClick: (Photo, View) -> Unit, onLongClick: (Photo, View) -> Unit) {
            // Show/hide the play overlay
            playOverlay.visibility = if (photo.isVideo) View.VISIBLE else View.GONE

            // Load thumbnail — Glide handles both image URIs and video URIs natively
            Glide.with(image)
                .load(photo.uri)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(image)

            image.transitionName = "photo_${photo.id}"
            itemView.setOnClickListener     { onClick(photo, image) }
            itemView.setOnLongClickListener { onLongClick(photo, image); true }
        }
    }
}
