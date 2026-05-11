package ca.thebikemechanic.thruspark.util

import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide cache of "which packages are allowed to post notifications right
 * now." Written by ProfileEngine on activate / deactivate; read by
 * ThruSparkNotificationListener on every onNotificationPosted callback.
 *
 * The listener fires on a binder-IPC thread separate from any coroutine scope,
 * so we use AtomicReference for thread-safe reads. A null allowlist means
 * "no profile active — don't filter anything."
 */
object NotificationFilterState {

    private val ref = AtomicReference<Set<String>?>(null)

    /** Active profile's combined allowlist: explicit packages + categories + system exemptions. */
    val allowlist: Set<String>? get() = ref.get()

    val isFiltering: Boolean get() = ref.get() != null

    fun setAllowlist(packages: Set<String>) {
        ref.set(packages)
    }

    fun clear() {
        ref.set(null)
    }

    /** True if this package is allowed to post notifications. */
    fun isAllowed(packageName: String): Boolean {
        val list = ref.get() ?: return true   // not filtering → everyone allowed
        return packageName in list
    }
}
