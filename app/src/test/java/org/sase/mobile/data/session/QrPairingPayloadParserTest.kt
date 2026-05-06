package org.sase.mobile.data.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QrPairingPayloadParserTest {
    @Test
    fun parsesDocumentedJsonPayload() {
        val payload = QrPairingPayloadParser.parse(
            """
            {
              "schema_version": 1,
              "type": "sase_mobile_pair",
              "base_url": "http://127.0.0.1:7629",
              "pairing_id": "pair_abc123",
              "code": "123456",
              "host_label": "workstation"
            }
            """.trimIndent(),
        )

        assertThat(payload.baseUrl).isEqualTo("http://127.0.0.1:7629/api/v1/")
        assertThat(payload.pairingId).isEqualTo("pair_abc123")
        assertThat(payload.code).isEqualTo("123456")
        assertThat(payload.hostLabel).isEqualTo("workstation")
    }

    @Test
    fun parsesDocumentedUriPayload() {
        val payload = QrPairingPayloadParser.parse(
            "sase://pair?base_url=http%3A%2F%2F127.0.0.1%3A7629&pairing_id=pair_abc123&code=123456",
        )

        assertThat(payload.baseUrl).isEqualTo("http://127.0.0.1:7629/api/v1/")
        assertThat(payload.pairingId).isEqualTo("pair_abc123")
        assertThat(payload.code).isEqualTo("123456")
        assertThat(payload.hostLabel).isNull()
    }

    @Test
    fun rejectsUnexpectedFieldsAndUnsafeUrls() {
        assertRejects(
            """
            {
              "schema_version": 1,
              "type": "sase_mobile_pair",
              "base_url": "file:///etc/passwd",
              "pairing_id": "pair_abc123",
              "code": "123456"
            }
            """.trimIndent(),
        )
        assertRejects(
            "sase://pair?base_url=http%3A%2F%2F127.0.0.1%3A7629&pairing_id=pair_abc123&code=123456&command=run",
        )
    }

    private fun assertRejects(payload: String) {
        try {
            QrPairingPayloadParser.parse(payload)
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected QR payload to be rejected")
    }
}

