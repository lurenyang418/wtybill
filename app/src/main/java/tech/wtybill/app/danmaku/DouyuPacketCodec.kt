package tech.wtybill.app.danmaku

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PacketDecodeResult(val bodies: List<String>, val remainder: ByteArray)

object DouyuPacketCodec {
    private const val HEADER_SIZE = 12
    private const val MAX_PACKET_LENGTH = 2 * 1024 * 1024

    fun encode(body: String, type: Int = 689): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val packetLength = bytes.size + 9
        val output = ByteArrayOutputStream(packetLength + 4)
        fun writeInt(value: Int) {
            output.write(value and 0xff)
            output.write((value ushr 8) and 0xff)
            output.write((value ushr 16) and 0xff)
            output.write((value ushr 24) and 0xff)
        }
        fun writeShort(value: Int) {
            output.write(value and 0xff)
            output.write((value ushr 8) and 0xff)
        }
        writeInt(packetLength)
        writeInt(packetLength)
        writeShort(type)
        output.write(0)
        output.write(0)
        output.write(bytes)
        output.write(0)
        return output.toByteArray()
    }

    fun decode(buffer: ByteArray): PacketDecodeResult {
        var offset = 0
        val bodies = mutableListOf<String>()
        while (buffer.size - offset >= HEADER_SIZE) {
            val packetLength = readInt(buffer, offset)
            val totalLength = packetLength + 4
            require(packetLength in 9..MAX_PACKET_LENGTH) { "douyu packet length out of bounds: $packetLength" }
            if (totalLength > buffer.size - offset) break
            require(readInt(buffer, offset + 4) == packetLength) { "douyu packet length mismatch" }
            val packetType = readShort(buffer, offset + 8)
            require(packetType == 689 || packetType == 690) { "unsupported douyu packet type: $packetType" }
            val bodyLength = packetLength - 9
            val bodyStart = offset + 12
            val nulIndex = bodyStart + bodyLength
            require(nulIndex < buffer.size && buffer[nulIndex].toInt() == 0) { "missing packet terminator" }
            bodies += buffer.copyOfRange(bodyStart, nulIndex).toString(Charsets.UTF_8)
            offset += totalLength
        }
        return PacketDecodeResult(bodies, buffer.copyOfRange(offset, buffer.size))
    }

    fun parseFields(body: String): Map<String, String> = body.split("/")
        .asSequence().filter { it.isNotEmpty() }.mapNotNull { field ->
            val separator = field.indexOf("@=")
            if (separator <= 0) return@mapNotNull null
            val key = field.substring(0, separator)
            val value = field.substring(separator + 2).replace("@S", "/").replace("@A", "@")
            key to value
        }.toMap()

    fun parseFieldObjects(body: String): List<Map<String, String>> = body
        .split("//")
        .asSequence()
        .filter { it.isNotEmpty() }
        .map(::parseFields)
        .toList()

    fun chatMessages(body: String): List<DanmakuMessage> = parseFieldObjects(body)
        .mapNotNull(::chatMessageFields)

    fun chatMessage(body: String): DanmakuMessage? = chatMessages(body).firstOrNull()

    private fun chatMessageFields(fields: Map<String, String>): DanmakuMessage? {
        if (fields["type"] != "chatmsg") return null
        if (!fields.containsKey("dms")) return null
        val text = fields["txt"] ?: return null
        val username = fields["nn"].orEmpty()
        val color = when (fields["col"]?.toIntOrNull()) {
            1 -> 0xffff0000.toInt()
            2 -> 0xff1e87f0.toInt()
            3 -> 0xff7ac84b.toInt()
            4 -> 0xffff7f00.toInt()
            5 -> 0xff9b39f4.toInt()
            6 -> 0xffff69b4.toInt()
            else -> 0xffffffff.toInt()
        }
        return DanmakuMessage(username, text, color)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun readShort(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
}
