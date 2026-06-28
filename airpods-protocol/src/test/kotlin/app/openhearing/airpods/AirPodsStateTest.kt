package app.openhearing.airpods

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 0 only exercises the pure model types. The protocol itself is UNVERIFIED
 * and is tested on real hardware in Phase 3 using the capture scripts in
 * docs/PROTOCOL.md — it cannot be meaningfully unit-tested here.
 */
class AirPodsStateTest {
    @Test
    fun `connected state carries model and per-ear battery`() {
        val state = AirPodsState.Connected(
            AirPodsModel.AIRPODS_PRO_2,
            leftBatteryPercent = 80,
            rightBatteryPercent = 75,
        )
        assertEquals(AirPodsModel.AIRPODS_PRO_2, state.model)
        assertEquals(80, state.leftBatteryPercent)
    }

    @Test
    fun `protocol-unavailable is a distinct, non-throwing outcome`() {
        val result: ConnectResult = ConnectResult.ProtocolUnavailable("control channel handshake unconfirmed")
        assertTrue(result is ConnectResult.ProtocolUnavailable)
    }
}
