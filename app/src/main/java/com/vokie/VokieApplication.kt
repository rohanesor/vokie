package com.vokie

import android.app.Application
import com.vokie.communication.BluetoothTransport

class VokieApplication : Application() {
    lateinit var bluetoothTransport: BluetoothTransport
        private set

    override fun onCreate() {
        super.onCreate()
        bluetoothTransport = BluetoothTransport(applicationContext)
    }
}
