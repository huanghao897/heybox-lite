package com.ronan.heyboxlite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Subscribes only while visible; CacheMaintenance owns I/O and completion ordering. */
@Composable
internal fun rememberStorageSnapshot(services: ComposeServices): DisposableStorage.Snapshot? {
    val maintenance = remember(services.maintenance, services.activity, services.cache, services.handler) {
        services.maintenance ?: CacheMaintenance(services.activity, services.cache, services.handler)
    }
    var snapshot by remember(maintenance) { mutableStateOf<DisposableStorage.Snapshot?>(null) }
    DisposableEffect(maintenance, services.activity) {
        val observer = CacheMaintenance.StorageCallback { snapshot = it }
        var subscribed = false
        fun start() {
            if (!subscribed) {
                subscribed = true
                maintenance.observeStorage(observer)
            }
        }
        fun stop() {
            if (subscribed) {
                subscribed = false
                maintenance.removeStorageObserver(observer)
            }
        }
        val lifecycle = (services.activity as? LifecycleOwner)?.lifecycle
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_DESTROY -> stop()
                else -> Unit
            }
        }
        lifecycle?.addObserver(lifecycleObserver)
        if (lifecycle == null || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose {
            stop()
            lifecycle?.removeObserver(lifecycleObserver)
            if (services.maintenance == null) maintenance.close()
        }
    }
    return snapshot
}

@Composable
internal fun ComposeStorageReadout(snapshot: DisposableStorage.Snapshot?) {
    if (snapshot == null) return
    WatchRow(title = "\u4fdd\u7559\u6570\u636e", value = StorageCleanupText.size(snapshot.retainedBytes))
    if (snapshot.deferredBytes > 0L) {
        WatchRow(title = "\u6682\u4e0d\u6e05\u7406", value = StorageCleanupText.size(snapshot.deferredBytes))
    }
    WatchRow(title = "\u5e94\u7528\u5b89\u88c5", value = StorageCleanupText.size(snapshot.installedCodeBytes))
    WatchRow(title = "\u8fd0\u884c\u5fc5\u9700", value = StorageCleanupText.size(snapshot.runtimeCodeBytes))
    if (snapshot.unrecognizedBytes > 0L) {
        WatchRow(title = "\u5176\u4ed6\u6587\u4ef6", value = StorageCleanupText.size(snapshot.unrecognizedBytes))
    }
    if (!snapshot.complete) WatchRow(title = "\u7edf\u8ba1\u72b6\u6001", value = "\u4e0d\u5b8c\u6574")
}
