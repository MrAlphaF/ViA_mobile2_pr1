package com.arturssilins.practical1

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.arturssilins.practical1.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var analytics: FirebaseAnalytics // 1. Declare Firebase
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Initialize Firebase
        analytics = Firebase.analytics

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 10)
        }

        binding.btnCapture.setOnClickListener { takePhoto() }

        binding.btnCloseSlider.setOnClickListener {
            binding.sliderContainer.visibility = View.GONE
            binding.btnCapture.visibility = View.VISIBLE
            logEvent("close_slider") // Log action
        }
    }

    // --- Helper to log events ---
    private fun logEvent(name: String) {
        val bundle = Bundle()
        bundle.putString("action_type", name)
        analytics.logEvent("user_interaction", bundle)
        Log.d("FIREBASE_LOG", "Event sent: $name")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("Camera", "Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val file = File(getExternalFilesDir(null), "IMG_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(output, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                logEvent("take_picture") // Log action
                showSlider()
            }
            override fun onError(exc: ImageCaptureException) {
                Log.e("Camera", "Photo capture failed: ${exc.message}", exc)
            }
        })
    }

    private fun showSlider() {
        val files = getExternalFilesDir(null)?.listFiles { f -> f.name.endsWith(".jpg") }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) return

        binding.viewPager.adapter = ImageAdapter(files)
        binding.sliderContainer.visibility = View.VISIBLE
        binding.btnCapture.visibility = View.GONE
        logEvent("show_slider")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_audio -> {
                logEvent("nav_to_audio")
                startActivity(Intent(this, AudioActivity::class.java))
                true
            }
            R.id.action_show_images -> {
                showSlider()
                true
            }
            R.id.action_delete -> {
                getExternalFilesDir(null)?.listFiles()?.forEach { it.delete() }
                Toast.makeText(this, "Deleted all images", Toast.LENGTH_SHORT).show()
                logEvent("delete_all_images")
                if (binding.sliderContainer.visibility == View.VISIBLE) {
                    binding.sliderContainer.visibility = View.GONE
                    binding.btnCapture.visibility = View.VISIBLE
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

class ImageAdapter(private val files: List<File>) : RecyclerView.Adapter<ImageAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) {
        Glide.with(holder.itemView).load(files[position]).into(holder.itemView.findViewById(R.id.imgSlider))
    }
    override fun getItemCount() = files.size
}