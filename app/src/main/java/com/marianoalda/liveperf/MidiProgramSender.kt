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
 * Opens a MIDI device input port (host → device) and sends Program Change.
 * Channel is 0-based (0 = MIDI channel 1).
 */
class MidiProgramSender(context: Context) {
    private val midiManager =
        context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var device: MidiDevice? = null
    private var inputPort: MidiInputPort? = null
    private var deviceName: String? = null

    var onStatus: ((connected: Boolean, name: String?) -> Unit)? = null

    fun start() {
        refreshDevice()
        midiManager.registerDeviceCallback(
            object : MidiManager.DeviceCallback() {
                override fun onDeviceAdded(info: MidiDeviceInfo) {
                    if (inputPort == null) refreshDevice()
                }

                override fun onDeviceRemoved(info: MidiDeviceInfo) {
                    if (device?.info?.id == info.id) {
                        closeDevice()
                        refreshDevice()
                    }
                }
            },
            mainHandler,
        )
    }

    fun stop() {
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

    private fun refreshDevice() {
        val candidates = midiManager.devices.filter { it.inputPortCount > 0 }
        val info = candidates.firstOrNull { it.type == MidiDeviceInfo.TYPE_USB }
            ?: candidates.firstOrNull()
        if (info == null) {
            closeDevice()
            onStatus?.invoke(false, null)
            return
        }
        if (device?.info?.id == info.id && inputPort != null) {
            onStatus?.invoke(true, deviceName)
            return
        }
        closeDevice()
        midiManager.openDevice(info, { opened ->
            if (opened == null) {
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
            deviceName = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
                ?: "MIDI ${info.id}"
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
    }
}
