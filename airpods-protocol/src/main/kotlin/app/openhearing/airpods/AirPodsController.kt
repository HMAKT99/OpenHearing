package app.openhearing.airpods

import kotlinx.coroutines.flow.Flow

/**
 * Control surface for AirPods Pro 2/3.
 *
 * !!! UNVERIFIED PROTOCOL !!!
 * The AirPods Bluetooth/L2CAP control protocol is REVERSE-ENGINEERED, not public.
 * This interface is modelled on the documented work in LibrePods and CAPod. Every
 * concrete implementation detail (packet formats, opcodes, the transparency/
 * hearing-assist control path) is UNVERIFIED until confirmed on real hardware.
 *
 * Per the project's protocol stance, OpenHearing works fully as an open
 * hearing-assist tool with ANY headset; this module is a best-effort enhancement
 * layer and MUST NOT be a hard dependency of the audio pipeline.
 *
 * Phase 0 ships interfaces + models only. Phase 3 attempts real detection,
 * battery/state reads, and transparency routing — each unverified path paired
 * with a concrete on-device capture/test script (see docs/PROTOCOL.md).
 */
interface AirPodsController {
    /** Emits the latest known device state, or [AirPodsState.Disconnected]. */
    val state: Flow<AirPodsState>

    /**
     * Attempt to connect to a previously bonded AirPods device and open the
     * control channel. UNVERIFIED: the control-channel handshake is assumed, not
     * confirmed. Returns the outcome rather than throwing on protocol failure.
     */
    suspend fun connect(): ConnectResult

    /** Close the control channel and stop observing the device. */
    suspend fun disconnect()
}

/** Which AirPods we believe we are talking to. Detection is itself UNVERIFIED. */
enum class AirPodsModel {
    AIRPODS_PRO_2,
    AIRPODS_PRO_3,
    UNKNOWN,
}

/** High-level connection/usage state surfaced to the rest of the app. */
sealed interface AirPodsState {
    data object Disconnected : AirPodsState

    data class Connected(val model: AirPodsModel, val leftBatteryPercent: Int?, val rightBatteryPercent: Int?) :
        AirPodsState
}

/** Result of attempting to open the (UNVERIFIED) control channel. */
sealed interface ConnectResult {
    data class Success(val model: AirPodsModel) : ConnectResult

    data object NoDeviceBonded : ConnectResult

    /** The control channel could not be established — expected while UNVERIFIED. */
    data class ProtocolUnavailable(val detail: String) : ConnectResult
}
