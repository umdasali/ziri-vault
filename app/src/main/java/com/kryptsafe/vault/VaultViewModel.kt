package com.kryptsafe.vault

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VaultRepository(app)

    var items by mutableStateOf<List<VaultItem>>(emptyList()); private set
    var busyMessage by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)
    /** Originals that were imported successfully and can be offered for deletion. */
    var deletable by mutableStateOf<List<Uri>>(emptyList())

    fun refresh() = viewModelScope.launch {
        items = withContext(Dispatchers.IO) { runCatching { repo.list() }.getOrDefault(emptyList()) }
    }

    fun clear() { items = emptyList(); deletable = emptyList() }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val ok = mutableListOf<Uri>(); var failed = 0
            withContext(Dispatchers.IO) {
                uris.forEachIndexed { i, u ->
                    busyMessage = "Encrypting ${i + 1} of ${uris.size}…"
                    runCatching { repo.import(u) }.onSuccess { ok += u }.onFailure { failed++ }
                }
            }
            busyMessage = null
            message = if (failed == 0) "Encrypted ${ok.size} file(s)" else "Encrypted ${ok.size}, $failed failed"
            deletable = ok
            refresh()
        }
    }

    fun delete(ids: Collection<String>) = viewModelScope.launch {
        withContext(Dispatchers.IO) { ids.forEach(repo::delete) }
        refresh()
    }

    fun export(ids: Collection<String>, tree: Uri) = viewModelScope.launch {
        var done = 0
        withContext(Dispatchers.IO) {
            val dir = DocumentFile.fromTreeUri(getApplication(), tree) ?: return@withContext
            ids.forEachIndexed { i, id ->
                busyMessage = "Restoring ${i + 1} of ${ids.size}…"
                val item = items.firstOrNull { it.id == id } ?: return@forEachIndexed
                runCatching {
                    val out = dir.createFile(item.meta.mime, item.meta.name) ?: error("create failed")
                    getApplication<Application>().contentResolver.openOutputStream(out.uri)!!.use { repo.exportTo(id, it) }
                    done++
                }
            }
        }
        busyMessage = null
        message = "Restored $done of ${ids.size} file(s)"
    }

    /** Fallback deletion for API < 30 or when the system delete dialog can't be used. */
    fun deleteOriginalsDirectly(uris: List<Uri>): Int {
        val cr = getApplication<Application>().contentResolver
        return uris.count { u ->
            runCatching { DocumentsContract.deleteDocument(cr, u) }.getOrDefault(false) ||
                runCatching { cr.delete(u, null, null) > 0 }.getOrDefault(false)
        }
    }

    fun thumbnail(id: String) = repo.thumbnail(id)
    fun openReader(id: String) = repo.openReader(id)
    fun itemFile(id: String) = repo.itemFileFor(id)
}
