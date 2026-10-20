package dev.ifsc

import java.io.BufferedInputStream
import java.io.DataInputStream

internal class BankBranches(
    val name: String?,
    val numericBranches: IntArray,
    val stringBranches: Set<String>,
)

internal class RawBank(
    val name: String?,
    val type: String?,
    val ifsc: String?,
    val micr: String?,
    val iin: String?,
    private val flags: Int,
) {
    val achCredit: Boolean get() = flags and (1 shl 0) != 0
    val achDebit: Boolean get() = flags and (1 shl 1) != 0
    val apbs: Boolean get() = flags and (1 shl 2) != 0
    val nachDebit: Boolean get() = flags and (1 shl 3) != 0
    val upi: Boolean get() = flags and (1 shl 4) != 0
    val imps: Boolean get() = flags and (1 shl 5) != 0
}

internal class Data(
    val version: String,
    val branchCount: Int,
    val banks: Map<String, BankBranches>,
    val bankNames: Map<String, String>,
    val sublets: Map<String, String>,
    val customSublets: List<Pair<String, String>>,
    val details: Map<String, RawBank>,
) {
    val bankCodes: Set<String> = banks.keys + bankNames.keys
}

internal object IfscData {
    private const val RESOURCE = "/dev/ifsc/ifsc-core.bin"
    private const val MAGIC = "IFSC"
    private const val FORMAT_VERSION = 1
    private const val MAX_STRING_BYTES = 16 * 1024
    private const val MAX_COLLECTION_SIZE = 1_000_000

    val data: Data by lazy(LazyThreadSafetyMode.PUBLICATION) { load() }

    private fun load(): Data {
        val stream = Ifsc::class.java.getResourceAsStream(RESOURCE)
            ?: throw IllegalStateException(
                "Bundled IFSC data is missing. Rebuild the library with tools/generate_data.py."
            )

        return DataInputStream(BufferedInputStream(stream)).use { input ->
            val magic = ByteArray(MAGIC.length)
            input.readFully(magic)
            check(String(magic, Charsets.US_ASCII) == MAGIC) { "Invalid IFSC data magic" }

            val format = input.readUnsignedByte()
            check(format == FORMAT_VERSION) { "Unsupported IFSC data format: $format" }

            val version = input.readString()
            val branchCount = input.readCount()

            val bankCount = input.readCount()
            val banks = LinkedHashMap<String, BankBranches>(bankCount)
            var parsedBranches = 0
            repeat(bankCount) {
                val bankCode = input.readFixedAscii(4)
                val name = input.readString().ifEmpty { null }

                val numericCount = input.readCount()
                val numeric = IntArray(numericCount)
                var previous = 0
                for (index in 0 until numericCount) {
                    previous += input.readVarInt()
                    numeric[index] = previous
                }

                val stringCount = input.readCount()
                val strings = LinkedHashSet<String>(stringCount)
                repeat(stringCount) { strings.add(input.readFixedAscii(6)) }

                parsedBranches += numericCount + stringCount
                banks[bankCode] = BankBranches(name, numeric, strings)
            }
            check(parsedBranches == branchCount) {
                "IFSC branch count mismatch: header=$branchCount parsed=$parsedBranches"
            }

            val subletCount = input.readCount()
            val sublets = LinkedHashMap<String, String>(subletCount)
            repeat(subletCount) {
                sublets[input.readFixedAscii(11)] = input.readFixedAscii(4)
            }

            val customCount = input.readCount()
            val customSublets = ArrayList<Pair<String, String>>(customCount)
            repeat(customCount) {
                customSublets.add(input.readString() to input.readString())
            }

            val detailsCount = input.readCount()
            val details = LinkedHashMap<String, RawBank>(detailsCount)
            repeat(detailsCount) {
                val code = input.readFixedAscii(4)
                val name = input.readString().ifEmpty { null }
                val type = input.readString().ifEmpty { null }
                val ifsc = input.readString().ifEmpty { null }
                val micr = input.readString().ifEmpty { null }
                val iin = input.readString().ifEmpty { null }
                val flags = input.readUnsignedByte()
                details[code] = RawBank(name, type, ifsc, micr, iin, flags)
            }

            val bankNameCount = input.readCount()
            val bankNames = LinkedHashMap<String, String>(bankNameCount)
            repeat(bankNameCount) {
                bankNames[input.readFixedAscii(4)] = input.readString()
            }

            check(input.read() == -1) { "Unexpected trailing bytes in IFSC data" }

            Data(
                version = version,
                branchCount = branchCount,
                banks = banks,
                bankNames = bankNames,
                sublets = sublets,
                customSublets = customSublets,
                details = details,
            )
        }
    }

    private fun DataInputStream.readVarInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val byte = readUnsignedByte()
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            check(shift <= 28) { "Malformed varint in IFSC data" }
        }
    }

    private fun DataInputStream.readCount(): Int {
        val count = readVarInt()
        check(count in 0..MAX_COLLECTION_SIZE) { "Invalid collection size in IFSC data: $count" }
        return count
    }

    private fun DataInputStream.readString(): String {
        val length = readCount()
        check(length <= MAX_STRING_BYTES) { "String too long in IFSC data: $length" }
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun DataInputStream.readFixedAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }
}
