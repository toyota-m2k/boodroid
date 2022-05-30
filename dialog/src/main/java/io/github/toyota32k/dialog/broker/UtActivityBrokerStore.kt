package io.github.toyota32k.dialog.broker

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

abstract class UtActivityBrokerStore {
    abstract val brokerList:List<IUtActivityBroker>

    fun register(activity:FragmentActivity) {
        for(broker in brokerList) {
            broker.register(activity)
        }
    }

    fun register(fragment: Fragment) {
        for(broker in brokerList) {
            broker.register(fragment)
        }
    }
}