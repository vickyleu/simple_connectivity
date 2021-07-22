// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package io.flutter.plugins.connectivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry.Registrar

/** ConnectivityPlugin  */
class ConnectivityPlugin : FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {

    private var manager: ConnectivityManager?=null
    private var receiver: BroadcastReceiver? = null
    private var channel: MethodChannel? = null
    private var binding: FlutterPlugin.FlutterPluginBinding? = null

    override fun onListen(arguments: Any?, events: EventSink) {
        receiver = createReceiver(events)
        binding?.applicationContext?.registerReceiver(receiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    override fun onCancel(arguments: Any?) {
        binding?.applicationContext?.unregisterReceiver(receiver)
        receiver = null
    }

    private fun getNetworkType(manager: ConnectivityManager?): String {
        if(manager!=null){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = manager.activeNetwork
                val capabilities = manager.getNetworkCapabilities(network) ?: return "none"
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return "wifi"
                }
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return "mobile"
                }
            }
        }
        return getNetworkTypeLegacy(manager)
    }

    private fun getNetworkTypeLegacy(manager: ConnectivityManager?): String {
        // handle type for Android versions less than Android 9
        val info = manager?.activeNetworkInfo
        if (info == null || !info.isConnected) {
            return "none"
        }
        val type = info.type
        return when (type) {
            ConnectivityManager.TYPE_ETHERNET, ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_WIMAX -> "wifi"
            ConnectivityManager.TYPE_MOBILE, ConnectivityManager.TYPE_MOBILE_DUN, ConnectivityManager.TYPE_MOBILE_HIPRI -> "mobile"
            else -> "none"
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "check" -> handleCheck(call, result)
            else -> result.notImplemented()
        }
    }

    private fun handleCheck(call: MethodCall, result: MethodChannel.Result) {
        result.success(checkNetworkType())
    }

    private fun checkNetworkType(): String {
        return getNetworkType(manager)
    }

    private val wifiInfo: WifiInfo?
        private get() {
            val wifiManager = binding?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            return wifiManager?.connectionInfo
        }

    private fun createReceiver(events: EventSink): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                events.success(checkNetworkType())
            }
        }
    }

    companion object {
        /** Plugin registration.  */
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val plugin = ConnectivityPlugin()
            plugin.setupChannel(registrar.messenger(), registrar.context())
        }
    }


    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        this.binding=binding
        setupChannel(binding.binaryMessenger, binding.applicationContext)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        this.binding=null
        teardownChannel()
    }

    fun setupChannel(messenger: BinaryMessenger, context: Context) {
        manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        channel = MethodChannel(messenger, "plugins.flutter.io/connectivity")
        channel?.setMethodCallHandler(this)
        val eventChannel = EventChannel(messenger, "plugins.flutter.io/connectivity_status")
        eventChannel.setStreamHandler(this)

    }

    private fun teardownChannel() {
        channel?.setMethodCallHandler(null)
        channel = null
    }
}