package com.kryptsafe.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** JVM tests can't load the native Argon2 library, so they use Bouncy Castle's pure-Java Argon2id (same algorithm, same output). */
fun installTestArgon() {
    KeyManager.argon2id = { pw, salt, mem, t, p ->
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt).withMemoryAsKB(mem).withIterations(t).withParallelism(p).build()
        ByteArray(32).also { Argon2BytesGenerator().apply { init(params) }.generateBytes(pw, it) }
    }
}
