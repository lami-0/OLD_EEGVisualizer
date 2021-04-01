package com.domain.eegvisualizer

import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.android.synthetic.main.activity_measure.*
import org.jetbrains.anko.toast
import java.io.IOException
import java.lang.Math.*
import java.util.*
import kotlin.collections.ArrayList

class MeasureActivity : AppCompatActivity() {
    companion object{
        var m_myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var m_bluetoothSocket: BluetoothSocket? = null
        lateinit var m_progress: ProgressDialog
        lateinit var m_bluetoothAdapter: BluetoothAdapter
        var m_isConnected: Boolean = false
        lateinit var m_address: String

        lateinit var thread: Thread
        var flag: Boolean = false
        var count = 1
        var signal_fps = 10
        var stream_time = 120

        var valueList = ArrayList<Float>()
        var fftWindow = ArrayList<Float>()
        lateinit var valueChart: LineChart
        lateinit var fftValueChart: BarChart
        private var values: ArrayList<Entry> = ArrayList()

        var plot_length = 50
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measure)
        m_address = intent.getStringExtra(MainActivity.EXTRA_ADDRESS).toString()

        ConnectToDevice(this).execute()
        valueChart = findViewById(R.id.value_chart)
        fftValueChart = findViewById(R.id.frequency_chart)

        for(i in 1 until plot_length + 1){
            values.add(Entry(i.toFloat(), 0F))
        }

        stream_start.setOnClickListener {
            if(!flag) {
                sendCommand("START")
                flag = true
                thread = StreamThread()
                thread.start()
            }else{ toast("Streaming is already started.") }
        }

        stream_pause.setOnClickListener{
            if(flag) {
                sendCommand("PAUSE")
                flag = false
            }else{ toast("Streaming is already paused.") }
        }

        control_disconnect.setOnClickListener{ disconnect() }

        show_result.setOnClickListener{
            val intent = Intent(this, AnalyzeActivity::class.java)
            startActivity(intent)
        }
    }

    inner class StreamThread: Thread(){
        override fun run() {
            super.run()
            while(count <= signal_fps * stream_time){
                if(m_bluetoothSocket != null){
                    try{
                        val buffer = ByteArray(3)
                        val length = m_bluetoothSocket!!.inputStream.read(buffer, 0, 3)
                        val v = intConversion(buffer)
                        runOnUiThread{ value_text.text = "$v" }

                        if(count % signal_fps == 0){
                            val remainMinute = ((signal_fps * stream_time) - count) / (60 * signal_fps)
                            val remainSecond = (((signal_fps * stream_time) - count) / signal_fps) % 60
                            val remainTime = "$remainMinute : $remainSecond"
                            runOnUiThread{ time_text.text = remainTime }
                        }

                        valueList.add(v.toFloat())
                        fftWindow.add(v.toFloat())
                        values.removeAt(0)
                        values.add(Entry((plot_length + count).toFloat(), v.toFloat()))
                        this@MeasureActivity.runOnUiThread(java.lang.Runnable {
                            val dataSet = LineDataSet(values, "Streaming Result")
                            dataSet.color = Color.BLUE
                            dataSet.setDrawCircles(false)
                            dataSet.setDrawCircleHole(false)
                            dataSet.valueTextSize = 0F

                            val dataList = ArrayList<ILineDataSet>()
                            dataList.add(dataSet)
                            val plotData = LineData(dataList)
                            value_chart.data = plotData
                            value_chart.invalidate()
                        })
                        count++
                    }catch(e: IOException){
                        e.printStackTrace()
                    }
                }
            }
            sendCommand("PAUSE")
            flag = false
            count = 0
        }
    }

    private fun intConversion(bytes: ByteArray): Int{
        var result = 0
        result += if(bytes[0] >= 0){ bytes[0] * (1 shl 16) }else{ (256 + bytes[0]) * (1 shl 16) }
        result += if(bytes[1] >= 0){ bytes[1] * (1 shl 8) }else{ (256 + bytes[1]) * (1 shl 8) }
        result += if(bytes[2] >= 0){ bytes[2] * (1 shl 0) }else{ (256 + bytes[2]) * (1 shl 0) }
        return result
    }

    private fun sendCommand(input: String){
        if(m_bluetoothSocket != null){
            try{
                m_bluetoothSocket!!.outputStream.write(input.toByteArray())
            }catch(e: IOException){
                e.printStackTrace()
            }
        }
    }

    private fun disconnect(){
        valueList.clear()
        values.clear()
        count = 0
        if(m_bluetoothSocket != null){
            try {
                m_bluetoothSocket!!.close()
                m_bluetoothSocket = null
                m_isConnected = false
            }catch(e: IOException){
                e.printStackTrace()
            }
        }
        finish()
    }

    private class ConnectToDevice(c: Context): AsyncTask<Void, Void, String>(){
        private var connectSuccess: Boolean = true
        private val context: Context

        init{
            this.context = c
        }

        override fun onPreExecute() {
            super.onPreExecute()
            m_progress = ProgressDialog.show(context, "Connecting...", "Please wait")
        }

        override fun doInBackground(vararg p0: Void?): String? {
            try{
                if(m_bluetoothSocket == null || m_isConnected){
                    m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device: BluetoothDevice = m_bluetoothAdapter.getRemoteDevice(m_address)
                    m_bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_myUUID)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    m_bluetoothSocket!!.connect()
                }
            }catch(e: IOException){
                connectSuccess = false
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if(!connectSuccess){
                Log.i("data", "Couldn't connect.")
            }else{
                m_isConnected = true
            }
            m_progress.dismiss()
        }
    }
}

class Complex(val re:Double, val im: Double){
    infix operator fun plus(x: Complex) = Complex(re + x.re, im + x.im)
    infix operator fun minus(x: Complex) = Complex(re - x.re, im - x.im)
    infix operator fun times(x: Double) = Complex(re * x, im * x)
    infix operator fun times(x: Complex) = Complex(re * x.re - im * x.im, re * x.im + im * x.re)
    infix operator fun div(x: Double) = Complex(re / x, im / x)
    val exp: Complex by lazy {
        Complex(kotlin.math.cos(im), kotlin.math.sin(im)) * (kotlin.math.cosh(re) + kotlin.math.sinh(re))}

    override fun toString() = when{
        b == "0.000" -> a
        a == "0.000" -> b + 'i'
        im > 0 -> a + '+' + b + 'i'
        else -> a + '-' + b + 'i'
    }

    private val a = "%1.3f".format(re)
    private val b = "%1.3f".format(kotlin.math.abs(im))
}

object FFT{
    fun fft(x: Array<Complex>) = _fft(x)
    fun rfft(x: Array<Complex>) = _fft(x)

    private fun _fft(x: Array<Complex>): Array<Complex> =
        if(x.size == 1)
            x
        else{
            val N = x.size
            require(N % 2 == 0, {"The Length should be even."})

            var (evens, odds) = Pair(emptyArray<Complex>(), emptyArray<Complex>())

            var (left, right) = Pair(emptyArray<Complex>(), emptyArray<Complex>())
            left + right
        }
}