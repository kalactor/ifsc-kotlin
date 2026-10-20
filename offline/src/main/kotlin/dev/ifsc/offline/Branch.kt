package dev.ifsc.offline

/**
 * A single bank branch from the bundled offline dataset.
 */
public data class Branch(
    public val ifsc: String,
    public val bankCode: String,
    public val bank: String,
    public val branch: String?,
    public val address: String?,
    public val city: String?,
    public val district: String?,
    public val state: String?,
    public val centre: String?,
    public val contact: String?,
    public val micr: String?,
    public val swift: String?,
    public val iso3166: String?,
    public val upi: Boolean,
    public val rtgs: Boolean,
    public val neft: Boolean,
    public val imps: Boolean,
)
