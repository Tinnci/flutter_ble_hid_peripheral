package jp.kshoji.blehid.util

import android.os.ParcelUuid
import androidx.annotation.NonNull
import java.util.UUID

/**
 * Utilities for BLE UUID
 *
 * @author K.Shoji
 */
object BleUuidUtils { // Changed to object for static-like access

    private const val UUID_LONG_STYLE_PREFIX = "0000"
    private const val UUID_LONG_STYLE_POSTFIX = "-0000-1000-8000-00805F9B34FB"

    // --- Helper constants for standard BLE short UUID checks ---
    // Mask to isolate the 16-bit field in the Most Significant Bits of a UUID. (e.g., 0x0000XXXX00000000L)
    private const val UUID_MSB_16BIT_FIELD_MASK = 0x0000FFFF00000000L
    // The Least Significant Bits of the standard Bluetooth Base UUID.
    private const val BLUETOOTH_BASE_UUID_LSB = -9223372036563946245L; // Equivalent to 0x800000805F9B34FBL
    // The part of the Most Significant Bits of the standard Bluetooth Base UUID that does NOT include the 16-bit field.
    // (i.e., 0x0000000000001000L for 0000xxxx-0000-1000-...)
    private const val BLUETOOTH_BASE_UUID_MSB_NON_FIELD_PART = 0x0000000000001000L
    // ---

    /**
     * Parses a UUID string with the format defined by toString().
     *
     * @param uuidString the UUID string to parse.
     * @return an UUID instance.
     * @throws NullPointerException if uuid is null.
     * @throws IllegalArgumentException if uuid is not formatted correctly.
     */
    @NonNull
    fun fromString(@NonNull uuidString: String): UUID {
        return try {
            UUID.fromString(uuidString)
        } catch (e: IllegalArgumentException) {
            // may be a short style
            UUID.fromString(UUID_LONG_STYLE_PREFIX + uuidString + UUID_LONG_STYLE_POSTFIX)
        }
    }

    /**
     * Obtains a UUID from Short style value.
     *
     * @param uuidShortValue the Short style UUID value.
     * @return an UUID instance.
     */
    @NonNull
    fun fromShortValue(uuidShortValue: Int): UUID {
        return UUID.fromString(UUID_LONG_STYLE_PREFIX + String.format("%04X", uuidShortValue and 0xffff) + UUID_LONG_STYLE_POSTFIX)
    }

    /**
     * Obtains a ParcelUuid from Short style value.
     *
     * @param uuidShortValue the Short style UUID value.
     * @return an UUID instance.
     */
    @NonNull
    fun parcelFromShortValue(uuidShortValue: Int): ParcelUuid {
        return ParcelUuid.fromString(UUID_LONG_STYLE_PREFIX + String.format("%04X", uuidShortValue and 0xffff) + UUID_LONG_STYLE_POSTFIX)
    }

    /**
     * UUID to short style value
     *
     * @param uuid the UUID
     * @return short style value, -1 if the specified UUID is not short style
     */
    fun toShortValue(@NonNull uuid: UUID): Int {
        return (uuid.mostSignificantBits shr 32 and 0xffffL).toInt()
    }

    /**
     * check if full style or short (16bits) style UUID matches
     *
     * @param src the UUID to be compared
     * @param dst the UUID to be compared
     * @return true if the both of UUIDs matches
     */
    fun matches(@NonNull src: UUID, @NonNull dst: UUID): Boolean {
        return if (isShortUuid(src) || isShortUuid(dst)) {
            // If at least one UUID is a standard BLE short UUID, compare them based on their 16-bit field.
            // This implies that if one is short, the other should also largely conform for a meaningful 16-bit comparison.
            val srcShortFieldVal = src.mostSignificantBits and UUID_MSB_16BIT_FIELD_MASK
            val dstShortFieldVal = dst.mostSignificantBits and UUID_MSB_16BIT_FIELD_MASK
            srcShortFieldVal == dstShortFieldVal
        } else {
            // Neither is a standard BLE short UUID (according to our strict check), perform full UUID comparison.
            src == dst
        }
    }

    /**
     * Check if the specified UUID style is short style (conforming to Bluetooth SIG base UUID).
     *
     * @param src the UUID
     * @return true if the UUID is short style
     */
    private fun isShortUuid(@NonNull src: UUID): Boolean {
        // A UUID is considered a "short UUID" if it conforms to the standard Bluetooth base UUID structure:
        // 1. Its LSB matches the Bluetooth Base UUID's LSB.
        // 2. The parts of its MSB, excluding the 16-bit field, match the Bluetooth Base UUID's MSB non-field part.
        val msbWithoutField = src.mostSignificantBits and UUID_MSB_16BIT_FIELD_MASK.inv() // .inv() avoids the problematic literal and gets the inverse mask
        return msbWithoutField == BLUETOOTH_BASE_UUID_MSB_NON_FIELD_PART &&
               src.leastSignificantBits == BLUETOOTH_BASE_UUID_LSB
    }
} 