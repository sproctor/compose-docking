package com.seanproctor.docking.persistence

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores the layout in a file, writing atomically via a temp-file rename. */
public class FileLayoutStorage(private val file: File) : LayoutStorage {

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        if (file.isFile) file.readText().takeIf { it.isNotBlank() } else null
    }

    override suspend fun save(text: String) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                // Windows can refuse to rename over an existing file.
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
