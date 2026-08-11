package tech.wtybill.app.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyuPacketCodecTest {
    @Test fun encodeUsesUtf8ByteLength() {
        val packet = DouyuPacketCodec.encode("type@=chatmsg/txt@=你好/")
        val result = DouyuPacketCodec.decode(packet)
        assertEquals(listOf("type@=chatmsg/txt@=你好/"), result.bodies)
        assertEquals(0, result.remainder.size)
    }

    @Test fun encodeMatchesFixedHexVector() {
        val packet = DouyuPacketCodec.encode("type@=mrkl/")
        assertEquals(
            "1400000014000000b102000074797065403d6d726b6c2f00",
            packet.joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
    }

    @Test fun decodeSupportsMultiplePacketsAndPartialRemainder() {
        val first = DouyuPacketCodec.encode("type@=mrkl/")
        val second = DouyuPacketCodec.encode("type@=chatmsg/nn@=a/txt@=b/")
        val combined = first + second
        val partial = DouyuPacketCodec.decode(combined.copyOfRange(0, combined.size - 2))
        assertEquals(listOf("type@=mrkl/"), partial.bodies)
        assertTrue(partial.remainder.isNotEmpty())
        assertEquals(listOf("type@=chatmsg/nn@=a/txt@=b/"), DouyuPacketCodec.decode(partial.remainder + combined.takeLast(2).toByteArray()).bodies)
    }

    @Test fun parsesEscapedFields() {
        val fields = DouyuPacketCodec.parseFields("type@=chatmsg/txt@=a@Sb@Ac/")
        assertEquals("a/b@c", fields["txt"])
    }

    @Test fun parsesChatMessageAndColor() {
        val message = DouyuPacketCodec.chatMessage("type@=chatmsg/dms@=1/nn@=主播/txt@=hello/col@=2/")
        assertEquals("主播", message?.username)
        assertEquals("hello", message?.text)
        assertEquals(0xff1e87f0.toInt(), message?.color)
    }

    @Test
    fun ignoresChatMessageWithoutDmsFieldUntilRealRoomValidation() {
        assertEquals(null, DouyuPacketCodec.chatMessage("type@=chatmsg/nn@=主播/txt@=hello/col@=2/"))
    }

    @Test
    fun distributesMultipleSttObjectsInOneBody() {
        val messages = DouyuPacketCodec.chatMessages(
            "type@=chatmsg/dms@=1/nn@=a/txt@=one//type@=chatmsg/dms@=1/nn@=b/txt@=two/",
        )
        assertEquals(listOf("one", "two"), messages.map { it.text })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedPacketLength() {
        val packet = DouyuPacketCodec.encode("type@=mrkl/")
        packet[4] = (packet[4].toInt() + 1).toByte()
        DouyuPacketCodec.decode(packet)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedPacketLength() {
        val packet = DouyuPacketCodec.encode("type@=mrkl/")
        packet[0] = 0x01
        packet[1] = 0x00
        packet[2] = 0x20
        packet[3] = 0x00
        DouyuPacketCodec.decode(packet)
    }
}
