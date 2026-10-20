package dev.ifsc

import java.util.Locale

/**
 * Offline IFSC validation and bank metadata.
 *
 * The dataset is bundled with the library, so every call is local and no
 * `INTERNET` permission is required.
 *
 * ```
 * Ifsc.validate("KKBK0000261")            // true
 * Ifsc.bankName("KKBK0000261")            // "Kotak Mahindra Bank"
 * Ifsc.bankDetails("PUNB")                // Bank(...)
 * ```
 *
 * This is an unofficial library. Data is sourced from
 * [razorpay/ifsc](https://github.com/razorpay/ifsc).
 */
public object Ifsc {

    /** Upstream dataset version, for example `v2.0.62`. */
    public val dataVersion: String
        get() = IfscData.data.version

    /** Number of IFSC codes in the bundled validation dataset. */
    public val branchCount: Int
        get() = IfscData.data.branchCount

    /** All bank codes known to the dataset, including historical codes. */
    public val bankCodes: Set<String>
        get() = IfscData.data.bankCodes

    /** Normalizes user input: trims whitespace and upper-cases it. */
    public fun normalize(code: String): String = code.trim().uppercase(Locale.ROOT)

    /**
     * Returns `true` when [code] is a well-formed IFSC code that exists in the
     * bundled dataset.
     */
    public fun validate(code: String): Boolean {
        val normalized = normalize(code)
        if (normalized.length != 11 || normalized[4] != '0') return false

        val bank = IfscData.data.banks[normalized.substring(0, 4)] ?: return false
        val branch = normalized.substring(5)

        return if (branch.all { it in '0'..'9' }) {
            val numeric = branch.toIntOrNull() ?: return false
            bank.numericBranches.binarySearch(numeric) >= 0
        } else {
            branch in bank.stringBranches
        }
    }

    /** Returns `true` when [bankCode] is a known four-letter bank code. */
    public fun validateBankCode(bankCode: String): Boolean {
        val normalized = normalize(bankCode)
        return normalized.length == 4 && normalized in IfscData.data.bankCodes
    }

    /**
     * Resolves the four-letter bank code for a bank code or a valid IFSC code.
     * Returns `null` for invalid input.
     */
    public fun bankCode(code: String): String? {
        val normalized = normalize(code)
        return when {
            normalized.length == 4 && validateBankCode(normalized) -> normalized
            normalized.length == 11 && validate(normalized) -> normalized.substring(0, 4)
            else -> null
        }
    }

    /**
     * Resolves the bank name for an IFSC code or a four-letter bank code.
     *
     * Sublet (sub-member) branches are resolved to the bank that actually owns
     * the branch. Returns `null` when the code is unknown or the dataset has no
     * display name for that bank.
     */
    public fun bankName(code: String): String? {
        val normalized = normalize(code)

        if (normalized.length == 4) {
            return IfscData.data.bankNames[normalized]
        }
        if (normalized.length != 11 || !validate(normalized)) {
            return null
        }

        IfscData.data.sublets[normalized]?.let { target ->
            return IfscData.data.bankNames[target] ?: target
        }
        for ((prefix, target) in IfscData.data.customSublets) {
            if (normalized.startsWith(prefix)) {
                return IfscData.data.bankNames[target] ?: target
            }
        }
        return IfscData.data.bankNames[normalized.substring(0, 4)]
    }

    /**
     * Returns metadata for a four-letter bank code, or `null` when the code is
     * not known at all.
     */
    public fun bankDetails(bankCode: String): Bank? {
        val normalized = normalize(bankCode)
        if (normalized.length != 4) return null

        val raw = IfscData.data.details[normalized]
        val name = IfscData.data.bankNames[normalized]
        if (raw == null && name == null) return null

        return Bank(
            code = normalized,
            name = name,
            type = raw?.type,
            ifsc = raw?.ifsc,
            micr = raw?.micr,
            iin = raw?.iin,
            achCredit = raw?.achCredit ?: false,
            achDebit = raw?.achDebit ?: false,
            apbs = raw?.apbs ?: false,
            nachDebit = raw?.nachDebit ?: false,
            upi = raw?.upi ?: false,
            imps = raw?.imps ?: false,
        )
    }
}
