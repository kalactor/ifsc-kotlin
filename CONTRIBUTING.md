# Contributing

## Data changes

Do not edit these files by hand:

- `core/src/main/resources/dev/ifsc/ifsc-core.bin`
- `offline/src/main/assets/ifsc.db`
- `offline/src/main/kotlin/dev/ifsc/offline/IfscOfflineData.kt`
- `core/src/test/resources/validator_asserts.json`

They are generated from the upstream release:

```bash
./tools/sync_upstream.sh v2.0.62
./gradlew :core:test :offline:testDebugUnitTest
```

The generator fails if the validation dataset and the branch database disagree
about which IFSC codes exist.

## Code changes

- Keep `core` free of Android dependencies so it stays usable on the JVM.
- Add a test for every validation, sublet, or bank-name edge case you fix.
- Run `./gradlew build` before opening a pull request.
- Do not change the public API without discussing it in an issue first.

## Reporting bad data

Data issues should be reported upstream at
[https://github.com/razorpay/ifsc/issues](https://github.com/razorpay/ifsc/issues).
Once upstream publishes a release, run the sync script here.
