package io.github.toyota32k.boodroid.common

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Parcelable

fun <T : Parcelable> Intent.compatGetParcelableExtra(name:String, clazz: Class<T>) : T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, clazz)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }
}

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun ContextWrapper.compatRegisterReceiver(receiver:BroadcastReceiver, filter: IntentFilter, exported:Boolean): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, if(exported) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(receiver, filter)
    }
}