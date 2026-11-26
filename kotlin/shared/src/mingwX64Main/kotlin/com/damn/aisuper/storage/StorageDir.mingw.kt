package com.damn.aisuper.storage

import platform.posix.EEXIST
import platform.posix.errno
import platform.posix.mkdir

internal actual fun ensureStorageDirectory(path: String): Boolean {
    val rc = mkdir(path)
    return rc == 0 || errno == EEXIST
}
