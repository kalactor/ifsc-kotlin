# Changelog

## 0.1.0 — 2026-10-20

Initial release.

- `core`: offline IFSC validation, bank code/name lookup, sublet resolution,
  and bank metadata. Pure Kotlin/JVM, no Android dependency.
- `offline`: bundled SQLite database with 183,214 branches, indexed lookup,
  text search, and per-bank branch listing.
- `sample`: Compose demo app.
- Dataset: `razorpay/ifsc` `v2.0.62` (2026-09-01).
- Verified against the upstream `validator_asserts.json` fixture (617 cases).
