package com.domain.eegvisualizer

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_analyze.*

class AnalyzeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analyze)

        back_button.setOnClickListener{
            finish()
        }
        more_about.setOnClickListener{
            val intent = Intent(this, WaveinfoActivity::class.java)
            startActivity(intent)
        }
    }
}