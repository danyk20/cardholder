package io.github.danyk20.cardholder.core.model

import kotlin.test.assertEquals
import org.junit.Test

class CardNetworkTest {
    @Test
    fun `detects networks from well-known test numbers`() {
        mapOf(
            "4111 1111 1111 1111" to CardNetwork.VISA,
            "5555555555554444" to CardNetwork.MASTERCARD,
            "2223003122003222" to CardNetwork.MASTERCARD,
            "378282246310005" to CardNetwork.AMERICAN_EXPRESS,
            "6011111111111117" to CardNetwork.DISCOVER,
            "3530111333300000" to CardNetwork.JCB,
            "36227206271667" to CardNetwork.DINERS_CLUB,
            "6200000000000005" to CardNetwork.UNIONPAY,
            "6759649826438453" to CardNetwork.MAESTRO,
            "9999" to CardNetwork.UNKNOWN,
            "" to CardNetwork.UNKNOWN,
        ).forEach { (number, expected) ->
            assertEquals(expected, CardNetwork.detect(number), "number=$number")
        }
    }

    @Test
    fun `american express uses four digit cvv`() {
        assertEquals(4, CardNetwork.AMERICAN_EXPRESS.cvvLength)
        assertEquals(3, CardNetwork.VISA.cvvLength)
    }
}
