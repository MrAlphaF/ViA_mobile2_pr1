package com.arturssilins.practical1

import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arturssilins.practical1.databinding.ActivityAudioBinding
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import java.io.File
import java.io.IOException

class AudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioBinding
    private lateinit var analytics: FirebaseAnalytics
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Audio Recorder"

        // Initialize Firebase
        analytics = Firebase.analytics

        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 20)
        setupList()

        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }
    }

    private fun logEvent(name: String) {
        val bundle = Bundle()
        bundle.putString("action_type", name)
        analytics.logEvent("user_interaction", bundle)
        Log.d("FIREBASE_LOG", "Event sent: $name")
    }

    private fun startRecording() {
        val file = File(externalCacheDir, "AUDIO_${System.currentTimeMillis()}.3gp")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(file.absolutePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            try {
                prepare()
                start()
                isRecording = true
                binding.btnRecord.text = "Stop Recording"
                binding.btnRecord.setBackgroundColor(getColor(android.R.color.holo_red_light))
                logEvent("start_recording")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        try { mediaRecorder?.apply { stop(); release() } } catch (e: Exception) { e.printStackTrace() }
        mediaRecorder = null
        isRecording = false
        binding.btnRecord.text = "Record Audio"
        binding.btnRecord.setBackgroundColor(getColor(com.google.android.material.R.color.design_default_color_primary))
        logEvent("stop_recording")
        setupList()
    }

    private fun setupList() {
        val files = externalCacheDir?.listFiles { f -> f.name.startsWith("AUDIO_") }
            ?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()

        binding.rvAudioList.layoutManager = LinearLayoutManager(this)
        binding.rvAudioList.adapter = AudioAdapter(files) { file ->
            playAudio(file)
        }
    }

    private fun playAudio(file: File) {
        mediaPlayer?.release()
        Toast.makeText(this, "Playing: ${file.name}", Toast.LENGTH_SHORT).show()
        logEvent("play_audio") // Log play action

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
            } catch (e: Exception) {
                Toast.makeText(this@AudioActivity, "Error playing file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_audio, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_camera -> {
                logEvent("nav_to_camera")
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                true
            }
            R.id.action_delete_audio -> {
                externalCacheDir?.listFiles()?.forEach { it.delete() }
                setupList()
                logEvent("delete_all_audio")
                Toast.makeText(this, "All audio deleted", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class AudioAdapter(private val files: List<File>, private val onClick: (File) -> Unit) : RecyclerView.Adapter<AudioAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) { val txt: TextView = v.findViewById(android.R.id.text1) }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return VH(v)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.txt.text = files[position].name
        holder.itemView.setOnClickListener { onClick(files[position]) }
    }
    override fun getItemCount() = files.size
}