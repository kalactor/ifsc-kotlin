package dev.ifsc

/**
 * Metadata for a bank, keyed by its four-letter code.
 *
 * Some fields are `null` when the upstream dataset does not have the value.
 */
public data class Bank(
    public val code: String,
    public val name: String?,
    public val type: String?,
    public val ifsc: String?,
    public val micr: String?,
    public val iin: String?,
    public val achCredit: Boolean,
    public val achDebit: Boolean,
    public val apbs: Boolean,
    public val nachDebit: Boolean,
    public val upi: Boolean,
    public val imps: Boolean,
)
