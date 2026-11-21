package com.marianoalda.liveperf

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Opens a **USB** MIDI device input port (host → interface) and sends Program Change.
 * Ignores virtual/app and Bluetooth MIDI devices.
 * Channel is 0-based (0 = MIDI channel 1).
 */
class MidiProgramSender(context: Context) {
    private val midiManager =
        context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var device: MidiDevice? = null
    private var inputPort: MidiInputPort? = null
    private var deviceName: String? = null
    private var deviceCallback: MidiManager.DeviceCallback? = null

    var onStatus: ((connected: Boolean, name: String?) -> Unit)? = null

    fun start() {
        if (deviceCallback == null) {
            deviceCallback = object : MidiManager.DeviceCallback() {
                override fun onDeviceAdded(info: MidiDeviceInfo) {
                    if (isUsbMidiOutputTarget(info)) {
                        refreshDevice(force = device?.info?.id != info.id)
                    }
                }

                override fun onDeviceRemoved(info: MidiDeviceInfo) {
                    if (device?.info?.id == info.id) {
                        closeDevice()
                        refreshDevice()
                    }
                }
            }
            midiManager.registerDeviceCallback(deviceCallback, mainHandler)
        }
        refreshDevice()
    }

    fun stop() {
        deviceCallback?.let { midiManager.unregisterDeviceCallback(it) }
        deviceCallback = null
        closeDevice()
    }

    fun sendProgramChange(program: Int, channel: Int = 0): Boolean {
        val port = inputPort ?: run {
            refreshDevice()
            inputPort
        } ?: return false
        val prog = program.coerceIn(0, 127)
        val ch = channel.coerceIn(0, 15)
        val status = (0xC0 or ch).toByte()
        val msg = byteArrayOf(status, prog.toByte())
        return try {
            port.send(msg, 0, msg.size)
            true
        } catch (e: Exception) {
            Log.w(TAG, "send failed", e)
            false
        }
    }

    /**
     * Silence stuck notes: All Sound Off (CC 120) + All Notes Off (CC 123)
     * on every MIDI channel.
     */
    fun sendPanic(): Boolean {
        val port = inputPort ?: run {
            refreshDevice()
            inputPort
        } ?: return false
        return try {
            for (ch in 0..15) {
                val status = (0xB0 or ch).toByte()
                port.send(byteArrayOf(status, 120.toByte(), 0), 0, 3)
                port.send(byteArrayOf(status, 123.toByte(), 0), 0, 3)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "panic failed", e)
            false
        }
    }

    private fun refreshDevice(force: Boolean = false) {
        val info = midiManager.devices.firstOrNull { isUsbMidiOutputTarget(it) }
        if (info == null) {
            closeDevice()
            onStatus?.invoke(false, null)
            return
        }
        if (!force && device?.info?.id == info.id && inputPort != null) {
            onStatus?.invoke(true, deviceName)
            return
        }
        closeDevice()
        midiManager.openDevice(info, { opened ->
            if (opened == null) {
                onStatus?.invoke(false, null)
                return@openDevice
            }
            // Re-check: only keep USB targets.
            if (!isUsbMidiOutputTarget(opened.info)) {
                opened.close()
                onStatus?.invoke(false, null)
                return@openDevice
            }
            val port = opened.openInputPort(0)
            if (port == null) {
                opened.close()
                onStatus?.invoke(false, null)
                return@openDevice
            }
            device = opened
            inputPort = port
            deviceName = displayName(opened.info)
            Log.i(TAG, "Opened USB MIDI: $deviceName type=${opened.info.type}")
            onStatus?.invoke(true, deviceName)
        }, mainHandler)
    }

    private fun closeDevice() {
        try {
            inputPort?.close()
        } catch (_: Exception) {
        }
        try {
            device?.close()
        } catch (_: Exception) {
        }
        inputPort = null
        device = null
        deviceName = null
        onStatus?.invoke(false, null)
    }

    companion object {
        private const val TAG = "MidiProgramSender"

        /** Device we can send TO over USB (app writes to device input port). */
        fun isUsbMidiOutputTarget(info: MidiDeviceInfo): Boolean {
            if (info.type != MidiDeviceInfo.TYPE_USB) return false
            if (info.inputPortCount <= 0) return false
            return true
        }

        fun displayName(info: MidiDeviceInfo): String {
            val product = info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            val manufacturer = info.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
            return listOfNotNull(manufacturer, product ?: name)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" ")
                .ifBlank { "USB #${info.id}" }
        }
    }
}
