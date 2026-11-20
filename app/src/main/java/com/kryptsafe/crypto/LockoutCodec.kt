package com.kryptsafe.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Serialises the lockout counters. The bytes are then encrypted + authenticated by the device key before hitting disk. */
object LockoutCodec {
    private const val VERSION = 1
    fun encode(map: Map<String, Long>): ByteArray = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).apply { writeInt(VERSION); writeInt(map.size); map.forEach { (k, v) -> writeUTF(k); writeLong(v) } }
    }.toByteArray()

    /** Returns null for anything malformed. Never throws. */
    fun decode(bytes: ByteArray): Map<String, Long>? = runCatching {
        DataInputStream(ByteArrayInputStream(bytes)).run {
            if (readInt() != VERSION) return null
            val n = readInt(); if (n !in 0..64) return null
            buildMap { repeat(n) { put(readUTF(), readLong()) } }
        }
    }.getOrNull()
}
