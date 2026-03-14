package com.lonx.audiotag.model

/**
 * Reading audio properties from a file can sometimes be very time consuming
 * and for the most accurate results can often involve reading the entire
 * file. Because in many situations speed is critical or the accuracy of the
 * values is not particularly important this allows the level of desired
 * accuracy to be set.
 */
public enum class AudioPropertiesReadStyle {
    /** Read as little of the file as possible */
    Fast,

    /** Read more of the file and make better values guesses */
    Average,

    /** Read as much of the file as needed to report accurate values */
    Accurate,
}
