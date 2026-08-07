package com.seanproctor.docking.model

import kotlin.random.Random

/**
 * Generates unique [NodeId]s. The random session prefix keeps freshly generated ids from
 * colliding with ids restored from a persisted layout.
 */
internal class NodeIdGenerator(
    private val prefix: String = randomPrefix(),
) {
    private var counter = 0

    fun next(): NodeId = NodeId("$prefix${counter++}")

    private companion object {
        const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

        fun randomPrefix(): String = buildString {
            repeat(4) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
            append('-')
        }
    }
}
