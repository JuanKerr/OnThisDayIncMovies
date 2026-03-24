package com.onthisday.app.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.onthisday.app.R
import com.onthisday.app.databinding.ActivityPhotoViewBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URIS      = "extra_uris"
        const val EXTRA_NAMES     = "extra_names"
        const val EXTRA_DATES     = "extra_dates"
        const val EXTRA_IS_VIDEO  = "extra_is_video"
        const val EXTRA_START_POS = "extra_start_pos"
        // Legacy single-photo extras (kept for widget/notification deep links)
        const val EXTRA_URI  = "extra_uri"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_DATE = "extra_date"
    }

    private lateinit var binding: ActivityPhotoViewBinding
    private var barsVisible = true

    private lateinit var uris:     Array<String>
    private lateinit var names:    Array<String>
    private lateinit var dates:    LongArray
    private lateinit var isVideos: BooleanArray
    private var currentPos = 0

    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var pendingDeleteUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        deleteRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                pendingDeleteUri?.let { finishDeleteFromMediaStore(it) }
            }
        }

        if (intent.hasExtra(EXTRA_URIS)) {
            uris     = intent.getStringArrayExtra(EXTRA_URIS)!!
            names    = intent.getStringArrayExtra(EXTRA_NAMES)!!
            dates    = intent.getLongArrayExtra(EXTRA_DATES)!!
            isVideos = intent.getBooleanArrayExtra(EXTRA_IS_VIDEO) ?: BooleanArray(uris.size) { false }
            currentPos = intent.getIntExtra(EXTRA_START_POS, 0)
        } else {
            uris     = arrayOf(intent.getStringExtra(EXTRA_URI) ?: run { finish(); return })
            names    = arrayOf(intent.getStringExtra(EXTRA_NAME) ?: "")
            dates    = longArrayOf(intent.getLongExtra(EXTRA_DATE, 0L))
            isVideos = BooleanArray(1) { false }
            currentPos = 0
        }

        setupPager()
    }

    private fun setupPager() {
        val pager = ViewPager2(this)
        pager.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

            override fun getItemCount() = uris.size

            override fun getItemViewType(position: Int) = if (isVideos[position]) 1 else 0

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return if (viewType == 1) {
                    // Video: thumbnail + tap-to-play overlay
                    val frame = android.widget.FrameLayout(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                    val thumb = ImageView(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    val playBtn = ImageView(parent.context).apply {
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                        ).also {
                            it.gravity = android.view.Gravity.CENTER
                        }
                        setImageResource(R.drawable.ic_play_circle)
                        // Scale up the play button for the full-screen view
                        scaleX = 2.5f
                        scaleY = 2.5f
                    }
                    frame.addView(thumb)
                    frame.addView(playBtn)
                    object : RecyclerView.ViewHolder(frame) {
                        val thumbnail: ImageView = thumb
                        val play: ImageView      = playBtn
                    }
                } else {
                    // Image: zoomable PhotoView
                    val pv = PhotoView(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setOnViewTapListener { _, _, _ -> toggleBars() }
                    }
                    object : RecyclerView.ViewHolder(pv) {}
                }
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val uri = Uri.parse(uris[position])
                if (isVideos[position]) {
                    // Cast to our anonymous video holder via field access
                    val frame = holder.itemView as android.widget.FrameLayout
                    val thumb = frame.getChildAt(0) as ImageView
                    val play  = frame.getChildAt(1) as ImageView

                    Glide.with(thumb.context)
                        .load(uri)
                        .centerInside()
                        .into(thumb)

                    // Tap anywhere on the frame to launch system video player
                    frame.setOnClickListener { launchVideoPlayer(uri) }
                    play.setOnClickListener  { launchVideoPlayer(uri) }
                } else {
                    Glide.with(holder.itemView.context)
                        .load(uri)
                        .into(holder.itemView as PhotoView)
                }
            }
        }

        val container = binding.photoView.parent as ViewGroup
        val idx = container.indexOfChild(binding.photoView)
        container.removeView(binding.photoView)
        container.addView(pager, idx)

        pager.setCurrentItem(currentPos, false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPos = position
                updateToolbar()
            }
        })

        updateToolbar()
    }

    private fun launchVideoPlayer(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.no_video_player, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateToolbar() {
        supportActionBar?.title = names[currentPos]
        val d = dates[currentPos]
        if (d > 0) {
            val fmt = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault())
            supportActionBar?.subtitle = fmt.format(Date(d))
        }
        invalidateOptionsMenu()
    }

    private fun toggleBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (barsVisible) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            supportActionBar?.hide()
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            supportActionBar?.show()
        }
        barsVisible = !barsVisible
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.photo_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home  -> { onBackPressedDispatcher.onBackPressed(); true }
            R.id.action_share  -> { shareCurrentItem(); true }
            R.id.action_delete -> { confirmDelete(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareCurrentItem() {
        val uri = Uri.parse(uris[currentPos])
        val mime = if (isVideos[currentPos]) "video/*" else "image/*"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_photo)))
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_photo)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> deleteCurrentItem() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteCurrentItem() {
        val uri = Uri.parse(uris[currentPos])
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                pendingDeleteUri = uri
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(deleteRequest).build())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    contentResolver.delete(uri, null, null)
                    finishDeleteFromMediaStore(uri)
                } catch (e: RecoverableSecurityException) {
                    pendingDeleteUri = uri
                    deleteRequestLauncher.launch(
                        IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                    )
                }
            } else {
                contentResolver.delete(uri, null, null)
                finishDeleteFromMediaStore(uri)
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishDeleteFromMediaStore(uri: Uri) {
        Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
        if (uris.size == 1) {
            setResult(Activity.RESULT_OK, Intent().putExtra("deleted", true))
            finish()
        } else {
            val newUris     = uris.toMutableList().also     { it.removeAt(currentPos) }.toTypedArray()
            val newNames    = names.toMutableList().also    { it.removeAt(currentPos) }.toTypedArray()
            val newDates    = dates.toMutableList().also    { it.removeAt(currentPos) }.toLongArray()
            val newIsVideos = isVideos.toMutableList().also { it.removeAt(currentPos) }.toBooleanArray()
            uris     = newUris
            names    = newNames
            dates    = newDates
            isVideos = newIsVideos
            setupPager()
            setResult(Activity.RESULT_OK, Intent().putExtra("deleted", true))
        }
        pendingDeleteUri = null
    }
}
