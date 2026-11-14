package com.liquid.liquidbaby

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.security.GeneralSecurityException

/** Message types. */
object T {
    const val HELLO_PAIR = 1
    const val PAIR_PUB = 2
    const val PAIR_CONFIRM = 3
    const val HELLO_AUTH = 5
    const val AUTH_REPLY = 6
    const val ERROR = 9
    const val SETTINGS = 10
    const val SET_LENGTH = 11
    const val SET_SENS = 12
    const val SYNC = 13
    const val CLIP_BEGIN = 14
    const val CLIP_DATA = 15
    const val CLIP_END = 16
    const val PING = 17
    const val PONG = 18
    const val SYNC_ACK = 19
    const val TALK_START = 20
    const val TALK_DATA = 21
    const val TALK_STOP = 22
    const val TALK_DENIED = 23
    const val VIEW_START = 24
    const val VIEW_STOP = 25
    const val VIEW_SET = 26
    const val VIDEO_FRAME = 27
    const val LIVE_AUDIO = 28
    const val VIEW_STATE = 29
    const val VIEW_ERROR = 30
    const val LISTEN_START = 31
    const val LISTEN_STOP = 32
    const val SET_INTERVAL = 33
    const val SET_TALKVOL = 34
    const val STATUS = 35
    const val DELETE_CLIP = 36
    const val DELETED_LIST = 37
    const val SUB_LEVEL = 38
    const val LEVEL = 39
    const val SYNC_END = 40
}

class Msg(val type: Int, val data: ByteArray)

/** A framed TCP connection. Before login messages are plain, afterwards AES-GCM. */
class Conn(val socket: Socket) {
    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
    private var aead: Aead? = null
    private val sendLock = Any()
    val clipLock = Any()

    fun enableCrypto(sessionKey: ByteArray, isParent: Boolean) {
        aead = if (isParent) Aead(sessionKey, 0, 1) else Aead(sessionKey, 1, 0)
    }

    fun send(type: Int, payload: ByteArray = ByteArray(0)) {
        synchronized(sendLock) {
            val body = ByteArray(1 + payload.size)
            body[0] = type.toByte()
            System.arraycopy(payload, 0, body, 1, payload.size)
            val a = aead
            val out = if (a != null) a.seal(body) else body
            output.writeInt(out.size)
            output.write(out)
            output.flush()
        }
    }

    fun read(): Msg {
        val len = input.readInt()
        val a = aead
        val limit = if (a != null) 1_500_000 else 4096
        if (len < 1 || len > limit) throw IOException("Bad frame size $len")
        val buf = ByteArray(len)
        input.readFully(buf)
        val body: ByteArray
        if (a != null) {
            try {
                body = a.open(buf)
            } catch (e: GeneralSecurityException) {
                throw IOException("Decryption failed (keys do not match)")
            }
        } else {
            body = buf
        }
        if (body.isEmpty()) throw IOException("Empty message")
        return Msg(body[0].toInt() and 0xff, body.copyOfRange(1, body.size))
    }

    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}
