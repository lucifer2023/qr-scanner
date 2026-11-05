package com.serialscanner.app

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val android.content.Context.store by preferencesDataStore("serials")

class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val key = stringPreferencesKey("items")
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items
    private val loaded = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch {
            val saved = getApplication<Application>().store.data.first()[key].orEmpty()
            _items.value = saved.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            loaded.complete(Unit)
        }
    }

    /** Adds only values not already in the list (scan order kept). Calls onAdded(fullList) if anything was new. */
    fun addScanned(values: List<String>, onAdded: (List<String>) -> Unit) {
        viewModelScope.launch {
            loaded.await()
            val current = _items.value
            val fresh = values.map { it.trim() }.filter { it.isNotEmpty() && it !in current }.distinct()
            if (fresh.isEmpty()) return@launch
            val all = current + fresh
            _items.value = all
            persist(all)
            onAdded(all)
        }
    }

    fun remove(value: String) {
        val all = _items.value - value
        _items.value = all
        persist(all)
    }

    fun clear() {
        _items.value = emptyList()
        persist(emptyList())
    }

    private fun persist(list: List<String>) {
        viewModelScope.launch {
            getApplication<Application>().store.edit { it[key] = list.joinToString("\n") }
        }
    }
}
