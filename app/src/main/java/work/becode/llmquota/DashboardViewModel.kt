package work.becode.llmquota

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Dashboard data and dialog flow. It outlives configuration changes, so rotation keeps the
 * account list, the fetched quotas and any open dialog, and does not refetch on its own.
 */
class DashboardViewModel(private val store: AccountStore) : ViewModel() {
    private val fetcher = QuotaFetcher(store)

    /** Accounts in display order; a drag reorders them before [saveOrder] persists the order. */
    var accounts by mutableStateOf(store.list())
        private set
    private val _results = mutableStateMapOf<String, SourceResult>()
    val results: Map<String, SourceResult> get() = _results
    private val loadingKeys = mutableStateMapOf<String, Boolean>()
    var pulling by mutableStateOf(false)
        private set
    var updated by mutableStateOf<Instant?>(null)
        private set

    /** Cards being removed play their exit animation first; the account leaves the store afterwards. */
    val leaving = mutableStateListOf<String>()

    var choosing by mutableStateOf(false)
    var loginProvider by mutableStateOf<String?>(null)
    var addingDeepSeek by mutableStateOf(false)
    var pendingRemoval by mutableStateOf<StoredAccount?>(null)

    init {
        refreshAll(showPull = false)
    }

    fun isLoading(key: String) = loadingKeys[key] == true

    /** Fetches one account on its own coroutine off the main thread; other rows are untouched. */
    fun refreshOne(account: StoredAccount): Job? {
        val k = accountKey(account)
        if (loadingKeys[k] == true) return null
        loadingKeys[k] = true
        return viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { fetcher.fetch(account.provider, account.id) }
            _results[k] = result
            loadingKeys.remove(k)
            updated = Instant.now()
        }
    }

    fun refreshAll(showPull: Boolean) {
        val jobs = accounts.mapNotNull { refreshOne(it) }
        if (showPull && jobs.isNotEmpty()) {
            pulling = true
            viewModelScope.launch { jobs.joinAll(); pulling = false }
        }
    }

    /** Shows [list] as the new order while a drag is in progress; nothing is stored yet. */
    fun moveTo(list: List<StoredAccount>) {
        accounts = list
    }

    fun saveOrder() = store.reorder(accounts.map { accountKey(it) })

    fun addAccount(account: StoredAccount) {
        store.upsert(account)
        accounts = store.list()
        refreshOne(account)
    }

    fun confirmRemoval(account: StoredAccount) {
        leaving.add(accountKey(account))
        pendingRemoval = null
    }

    /** Called once the exit animation of a removed card has finished. */
    fun finishRemoval(account: StoredAccount) {
        val k = accountKey(account)
        store.remove(account.provider, account.id)
        accounts = store.list()
        _results.remove(k)
        leaving.remove(k)
    }
}
