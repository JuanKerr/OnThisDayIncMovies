package com.onthisday.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.onthisday.app.R
import com.onthisday.app.data.GalleryItem
import com.onthisday.app.data.Photo
import com.onthisday.app.databinding.ActivityMainBinding
import com.onthisday.app.notification.DailyNotificationScheduler
import java.util.Calendar
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: GalleryAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        if (granted) { viewModel.loadBuckets(); viewModel.loadPhotos() }
        else {
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyText.setText(R.string.permission_denied)
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) DailyNotificationScheduler.schedule(this)
    }

    // Result from PhotoViewActivity — reload if a photo was deleted
    private val photoViewerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK &&
            result.data?.getBooleanExtra("deleted", false) == true) {
            viewModel.loadPhotos()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        setupRecyclerView()
        observeViewModel()
        checkPermissionAndLoad()
        scheduleNotificationIfAllowed()

        binding.btnPickDate.setOnClickListener { showDatePicker() }
    }

    private fun setupRecyclerView() {
        val spanCount = 3
        val glm = GridLayoutManager(this, spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.getItemViewType(position) == GalleryAdapter.VIEW_HEADER) spanCount else 1
            }
        }
        adapter = GalleryAdapter(::onPhotoClick, ::onPhotoLongClick)
        binding.recyclerView.layoutManager = glm
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(false)
    }

    private fun observeViewModel() {
        viewModel.galleryItems.observe(this) { items ->
            adapter.submitList(items)
            val empty = items.isEmpty()
            binding.emptyView.visibility   = if (empty) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        }
        viewModel.loading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading) {
                binding.emptyView.visibility    = View.VISIBLE
                binding.emptyText.setText(R.string.indexing)
                binding.recyclerView.visibility = View.GONE
            }
        }
        viewModel.selectedDateLabel.observe(this) { binding.tvSelectedDate.text = it }
    }

    private fun checkPermissionAndLoad() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            viewModel.loadBuckets()
            viewModel.loadPhotos()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun scheduleNotificationIfAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                DailyNotificationScheduler.schedule(this)
            } else {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            DailyNotificationScheduler.schedule(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> { showSettingsDialog(); true }
        else -> super.onOptionsItemSelected(item)
    }

    // ── Settings flyout ───────────────────────────────────────────────────────

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)

        val cbMovies   = view.findViewById<CheckBox>(R.id.cbIncludeMovies)
        val tvFolders  = view.findViewById<TextView>(R.id.tvSelectFolders)
        val tvAbout    = view.findViewById<TextView>(R.id.tvAbout)

        cbMovies.isChecked = viewModel.getIncludeMovies()

        val dialog = AlertDialog.Builder(this, R.style.Theme_OnThisDay_Dialog)
            .setTitle(R.string.settings)
            .setView(view)
            .setPositiveButton(R.string.apply) { _, _ ->
                viewModel.setIncludeMovies(cbMovies.isChecked)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        tvFolders.setOnClickListener {
            dialog.dismiss()
            showFolderPicker()
        }

        tvAbout.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, AboutActivity::class.java))
        }

        dialog.show()
    }

    private fun showDatePicker() {
        val utc = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(utc).apply {
            set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
            set(Calendar.MONTH, viewModel.selectedMonth - 1)
            set(Calendar.DAY_OF_MONTH, viewModel.selectedDay)
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);       set(Calendar.MILLISECOND, 0)
        }
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_date_title)
            .setSelection(cal.timeInMillis)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val sel = Calendar.getInstance(utc).apply { timeInMillis = millis }
            viewModel.setDate(sel.get(Calendar.MONTH) + 1, sel.get(Calendar.DAY_OF_MONTH))
        }
        picker.show(supportFragmentManager, "date_picker")
    }

    private fun showFolderPicker() {
        val buckets = viewModel.allBuckets.value ?: emptyList()
        if (buckets.isEmpty()) { Toast.makeText(this, R.string.no_folders_found, Toast.LENGTH_SHORT).show(); return }
        val treeAdapter = FolderTreeAdapter(buckets, viewModel.getSelectedFolders())
        val recycler = androidx.recyclerview.widget.RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = treeAdapter; setPadding(0, 8, 0, 8)
        }
        AlertDialog.Builder(this, R.style.Theme_OnThisDay_Dialog)
            .setTitle(R.string.select_folders).setView(recycler)
            .setPositiveButton(R.string.apply) { _, _ -> viewModel.setSelectedFolders(treeAdapter.getSelectedFolders()) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun onPhotoClick(photo: Photo, sharedView: View) {
        val allPhotos = viewModel.galleryItems.value
            ?.filterIsInstance<GalleryItem.PhotoItem>()
            ?.map { it.photo }
            ?: listOf(photo)
        val startPos = allPhotos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)

        val intent = Intent(this, PhotoViewActivity::class.java).apply {
            putExtra(PhotoViewActivity.EXTRA_URIS,      allPhotos.map { it.uri.toString() }.toTypedArray())
            putExtra(PhotoViewActivity.EXTRA_NAMES,     allPhotos.map { it.displayName }.toTypedArray())
            putExtra(PhotoViewActivity.EXTRA_DATES,     allPhotos.map { it.dateTaken }.toLongArray())
            putExtra(PhotoViewActivity.EXTRA_IS_VIDEO,  allPhotos.map { it.isVideo }.toBooleanArray())
            putExtra(PhotoViewActivity.EXTRA_START_POS, startPos)
        }
        photoViewerLauncher.launch(intent)
    }

    private fun onPhotoLongClick(photo: Photo, sharedView: View) {
        val options = arrayOf(
            getString(R.string.share_photo),
            getString(R.string.delete_photo)
        )
        AlertDialog.Builder(this, R.style.Theme_OnThisDay_Dialog)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> sharePhoto(photo)
                    1 -> confirmDeleteFromGrid(photo)
                }
            }.show()
    }

    private fun sharePhoto(photo: Photo) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (photo.isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, photo.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_photo)))
    }

    private fun confirmDeleteFromGrid(photo: Photo) {
        AlertDialog.Builder(this, R.style.Theme_OnThisDay_Dialog)
            .setTitle(R.string.delete_photo)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                try {
                    contentResolver.delete(photo.uri, null, null)
                    viewModel.loadPhotos()
                    Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    onPhotoClick(photo, View(this))
                    Toast.makeText(this, R.string.delete_open_viewer, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
}
