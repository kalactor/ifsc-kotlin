# IFSC Kotlin

Offline IFSC validation and bank/branch lookup for **Android and JVM**, written in Kotlin.

This is an **unofficial** library built on the data from
[razorpay/ifsc](https://github.com/razorpay/ifsc). It is not affiliated with,
endorsed by, or maintained by Razorpay.

```kotlin
Ifsc.validate("KKBK0000261")                  // true
Ifsc.bankName("KKBK0000261")                  // "Kotak Mahindra Bank"
Ifsc.bankName("HDFC0CKUB01")                  // "Khamgaon Urban Co-operative Bank" (sublet)
Ifsc.bankDetails("PUNB")?.micr                // "110024001"
```

## Why

The upstream project ships PHP, Ruby, Node.js, and Go packages, but no Kotlin
artifact. This library fills that gap:

- **No network calls** — validation and branch lookup work completely offline.
- **No `INTERNET` permission** — useful for privacy-sensitive and banking apps.
- **Full branch data** — 183,214 IFSC codes with address, city, district, state,
  MICR, SWIFT, and UPI/RTGS/NEFT/IMPS flags.
- **Sublet-aware bank names** — sub-member branches resolve to the bank that
  actually owns the branch.
- **Small core** — the validation dataset is a 505 KB binary resource, not a
  parsed 1 MB JSON file.

## Modules

| Artifact | Size | What it does |
|---|---|---|
| `core` | ~175 KB | Validation, bank code/name lookup, sublet resolution, bank metadata. Pure Kotlin/JVM. |
| `offline` | ~13 MB AAR | SQLite database of 183k branches, indexed lookup and search. Android only. Includes `core`. |
| `sample` | — | Compose demo app showing validation and offline branch lookup. |

Requirements: JVM 11+ for `core`; Android 7.0+ (minSdk 24) for `offline`.

> The offline database is 41 MiB once installed on the device. The first
> `IfscDatabase.open()` copies it from assets into the app's database directory.
> Plan for that storage cost, or use only `core` if you do not need branch
> addresses.

## Install (JitPack)

Published under `com.github.kalactor.ifsc-kotlin`.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.kalactor.ifsc-kotlin:core:0.1.0")

    // Optional: full offline branch details. Pulls in core automatically.
    implementation("com.github.kalactor.ifsc-kotlin:offline:0.1.0")
}
```

## Core usage

```kotlin
import dev.ifsc.Ifsc

Ifsc.validate("KKBK0000261")       // true
Ifsc.validate("kkbk0000261")       // true (input is trimmed and upper-cased)
Ifsc.validate("BOTM0XEEMRA")       // false

Ifsc.validateBankCode("PUNB")      // true
Ifsc.bankCode("KKBK0000261")       // "KKBK"

Ifsc.bankName("KKBK0000261")       // "Kotak Mahindra Bank"
Ifsc.bankName("KKBK")              // "Kotak Mahindra Bank"
Ifsc.bankName("KSCB0006001")       // "Tumkur District Central Bank"

val punb = Ifsc.bankDetails("PUNB")
punb?.name                          // "Punjab National Bank"
punb?.type                          // "PSB"
punb?.micr                          // "110024001"
punb?.upi                           // true
```

`bankName` returns `null` for an unknown code, or when the upstream dataset has
no display name for that bank. Use `validateBankCode` / `validate` to
distinguish "unknown" from "known but unnamed".

## Offline branch lookup

```kotlin
import dev.ifsc.offline.IfscDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val database = IfscDatabase.openAsync(context) // copies the asset on first use

val branch = database.lookupAsync("HDFC0000001")
branch?.bank       // "HDFC Bank"
branch?.branch     // "TULSIANI CHMBRS - NARIMAN PT"
branch?.address    // "101-104 TULSIANI CHAMBERS..."
branch?.micr       // "400240003"
branch?.swift      // "HDFCINBB"
branch?.upi        // true

val results = database.searchAsync("NARIMAN", limit = 10)
val hdfcBranches = database.branchesOfBankAsync("HDFC", limit = 20)

database.close()
```

`lookup`, `search`, `branchesOfBank`, and `countBranches` are synchronous. Call
them off the main thread, or use the `*Async` variants.

## Data updates

The bundled dataset is generated from a pinned upstream release (currently
`v2.0.62`, 2026-09-01).

```bash
./tools/sync_upstream.sh v2.0.62
./gradlew :core:test :offline:testDebugUnitTest
```

The sync script downloads the upstream tag and `by-bank.tar.gz`, then regenerates:

- `core/src/main/resources/dev/ifsc/ifsc-core.bin`
- `offline/src/main/assets/ifsc.db`
- `offline/src/main/kotlin/dev/ifsc/offline/IfscOfflineData.kt`
- `core/src/test/resources/validator_asserts.json`

The generator verifies that the validation dataset and the branch database
contain exactly the same 183,214 codes before writing anything.

The GitHub Actions workflow in `.github/workflows/sync-upstream.yml` runs this
monthly and opens a pull request.

## Build locally

Requirements: JDK 17+ (JDK 21 recommended), Android SDK 36, Python 3.

```bash
# local.properties
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew :core:test :offline:testDebugUnitTest :sample:assembleDebug
```

By default the sample uses the local `core` and `offline` modules. To verify
the published JitPack artifact instead:

```bash
./gradlew :sample:assembleDebug -PusePublishedIfsc=true
```

The generated data is committed, so a normal build does not need network access
beyond Gradle dependencies.

## Publishing

The project is set up for JitPack multi-module publishing:

1. Push the repo to `https://github.com/kalactor/ifsc-kotlin`.
2. Create a tag, for example `0.1.0`, and a GitHub release.
3. Open `https://jitpack.io/#kalactor/ifsc-kotlin` and trigger the build.

`jitpack.yml` publishes `core` and `offline` to the local Maven repository.

## License and attribution

- Library code: MIT. See [LICENSE](LICENSE).
- Dataset: sourced from [razorpay/ifsc](https://github.com/razorpay/ifsc).
  The upstream code is MIT-licensed and the dataset is public domain.
- See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

"Razorpay" and "IFSC" are used only to describe the data source. This project
is not an official Razorpay product.
