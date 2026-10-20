#!/usr/bin/env python3
"""Generate the binary validation dataset, SQLite branch database, and
generated Kotlin constants from the upstream razorpay/ifsc data.

Usage:
    python3 tools/generate_data.py \
        --upstream /path/to/razorpay/ifsc \
        --by-bank /path/to/by-bank \
        --version v2.0.62 \
        --out .

`--by-bank` must point at the extracted `by-bank/` directory from the
upstream release asset `by-bank.tar.gz`.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import glob
import json
import sqlite3
import sys
from pathlib import Path
from typing import NoReturn


MAGIC = b"IFSC"
FORMAT_VERSION = 1


def fail(message: str) -> NoReturn:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(1)


def write_varint(out, value: int) -> None:
    if value < 0:
        fail(f"negative varint: {value}")
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.write(bytes((byte | 0x80,)))
        else:
            out.write(bytes((byte,)))
            return


def write_string(out, value: str) -> None:
    data = value.encode("utf-8")
    write_varint(out, len(data))
    out.write(data)


def write_fixed(out, value: str, length: int) -> None:
    data = value.encode("ascii")
    if len(data) != length:
        fail(f"expected {length} ASCII characters, got {value!r}")
    out.write(data)


def expand_ifsc_codes(ifsc_data: dict) -> set[str]:
    codes: set[str] = set()
    for bank_code, branches in ifsc_data.items():
        if len(bank_code) != 4:
            fail(f"invalid bank code in IFSC.json: {bank_code!r}")
        for branch in branches:
            if isinstance(branch, int):
                branch_str = f"{branch:06d}"
            elif isinstance(branch, str):
                branch_str = branch
            else:
                fail(f"unexpected branch value for {bank_code}: {branch!r}")
            code = f"{bank_code}0{branch_str}"
            if len(code) != 11:
                fail(f"invalid expanded IFSC code: {code!r}")
            if code in codes:
                fail(f"duplicate IFSC code: {code}")
            codes.add(code)
    return codes


def load_branch_database(by_bank_dir: Path) -> dict[str, dict]:
    branches: dict[str, dict] = {}
    for path in sorted(glob.glob(str(by_bank_dir / "*.json"))):
        with open(path, encoding="utf-8") as handle:
            bank_data = json.load(handle)
        for code, row in bank_data.items():
            if code in branches:
                fail(f"duplicate branch in release data: {code}")
            branches[code] = row
    return branches


def write_core_binary(
    out_path: Path,
    version: str,
    ifsc_data: dict,
    branch_codes: set[str],
    bank_names: dict[str, str],
    banks: dict[str, dict],
    sublets: dict[str, str],
    custom_sublets: dict[str, str],
) -> None:
    numeric_total = 0
    string_total = 0

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "wb") as out:
        out.write(MAGIC)
        out.write(bytes((FORMAT_VERSION,)))
        write_string(out, version)
        write_varint(out, len(branch_codes))

        bank_codes = sorted(ifsc_data)
        write_varint(out, len(bank_codes))
        for bank_code in bank_codes:
            branches = ifsc_data[bank_code]
            numeric = sorted(branch for branch in branches if isinstance(branch, int))
            strings = sorted(branch for branch in branches if isinstance(branch, str))
            numeric_total += len(numeric)
            string_total += len(strings)

            write_fixed(out, bank_code, 4)
            write_string(out, bank_names.get(bank_code, ""))

            write_varint(out, len(numeric))
            previous = 0
            for value in numeric:
                if value < previous:
                    fail(f"numeric branches are not sorted for {bank_code}")
                write_varint(out, value - previous)
                previous = value

            write_varint(out, len(strings))
            for value in strings:
                write_fixed(out, value, 6)

        write_varint(out, len(sublets))
        for code in sorted(sublets):
            write_fixed(out, code, 11)
            write_fixed(out, sublets[code], 4)

        ordered_custom = sorted(custom_sublets.items(), key=lambda item: (-len(item[0]), item[0]))
        write_varint(out, len(ordered_custom))
        for prefix, target in ordered_custom:
            write_string(out, prefix)
            write_string(out, target)

        details = sorted(banks.items())
        write_varint(out, len(details))
        for code, row in details:
            write_fixed(out, code, 4)
            write_string(out, bank_names.get(code, ""))
            write_string(out, row.get("type") or "")
            write_string(out, row.get("ifsc") or "")
            write_string(out, row.get("micr") or "")
            write_string(out, row.get("iin") or "")
            flags = 0
            for bit, key in enumerate(
                ("ach_credit", "ach_debit", "apbs", "nach_debit", "upi", "imps")
            ):
                if row.get(key):
                    flags |= 1 << bit
            out.write(bytes((flags,)))

        # The display-name list is larger than the validation dataset (it also
        # contains historical/merged bank codes), so store it separately.
        write_varint(out, len(bank_names))
        for code in sorted(bank_names):
            write_fixed(out, code, 4)
            write_string(out, bank_names[code])

    print(
        f"core: {len(bank_codes)} banks, {numeric_total} numeric + "
        f"{string_total} string branches, {len(details)} bank records"
    )


def create_branch_database(
    db_path: Path,
    branches: dict[str, dict],
    metadata: dict[str, str],
) -> None:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()

    connection = sqlite3.connect(str(db_path))
    try:
        connection.execute("PRAGMA journal_mode=OFF")
        connection.execute("PRAGMA synchronous=OFF")
        connection.execute("PRAGMA temp_store=MEMORY")
        connection.execute(
            """
            CREATE TABLE metadata (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            )
            """
        )
        connection.execute(
            """
            CREATE TABLE branches (
                ifsc TEXT PRIMARY KEY NOT NULL,
                bank_code TEXT NOT NULL,
                bank TEXT NOT NULL,
                branch TEXT,
                address TEXT,
                city TEXT,
                district TEXT,
                state TEXT,
                centre TEXT,
                contact TEXT,
                micr TEXT,
                swift TEXT,
                iso3166 TEXT,
                upi INTEGER NOT NULL DEFAULT 0,
                rtgs INTEGER NOT NULL DEFAULT 0,
                neft INTEGER NOT NULL DEFAULT 0,
                imps INTEGER NOT NULL DEFAULT 0
            )
            """
        )
        connection.executemany(
            "INSERT INTO metadata(key, value) VALUES (?, ?)",
            sorted(metadata.items()),
        )

        rows = []
        for code in sorted(branches):
            row = branches[code]
            rows.append(
                (
                    code,
                    code[:4],
                    row.get("BANK") or "",
                    row.get("BRANCH"),
                    row.get("ADDRESS"),
                    row.get("CITY"),
                    row.get("DISTRICT"),
                    row.get("STATE"),
                    row.get("CENTRE"),
                    row.get("CONTACT"),
                    row.get("MICR"),
                    row.get("SWIFT"),
                    row.get("ISO3166"),
                    1 if row.get("UPI") else 0,
                    1 if row.get("RTGS") else 0,
                    1 if row.get("NEFT") else 0,
                    1 if row.get("IMPS") else 0,
                )
            )
        connection.executemany(
            "INSERT INTO branches VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            rows,
        )

        # The primary key covers exact IFSC lookups. A bank_code index keeps
        # "all branches of a bank" queries fast; leading-wildcard text search
        # cannot use a B-tree index anyway, so we avoid paying for more indexes.
        connection.execute("CREATE INDEX idx_branches_bank_code ON branches(bank_code)")
        connection.execute("PRAGMA user_version=1")
        connection.commit()
        connection.execute("VACUUM")
    finally:
        connection.close()

    size_mb = db_path.stat().st_size / (1024 * 1024)
    print(f"offline: {len(branches)} branches, {size_mb:.1f} MiB SQLite")


def write_generated_kotlin(
    path: Path,
    version: str,
    generated_at: str,
    branch_count: int,
    bank_count: int,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        f"""// Generated by tools/generate_data.py. Do not edit by hand.
package dev.ifsc.offline

/** Metadata about the bundled offline dataset. */
public object IfscOfflineData {{
    /** Upstream razorpay/ifsc release tag. */
    public const val VERSION: String = {json.dumps(version)}

    /** Date on which this dataset was generated (UTC). */
    public const val GENERATED_AT: String = {json.dumps(generated_at)}

    /** Number of IFSC codes in the bundled database. */
    public const val BRANCH_COUNT: Int = {branch_count}

    /** Number of distinct bank codes in the bundled database. */
    public const val BANK_COUNT: Int = {bank_count}
}}
""",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", required=True, type=Path, help="razorpay/ifsc checkout")
    parser.add_argument("--by-bank", required=True, type=Path, help="extracted by-bank directory")
    parser.add_argument("--version", required=True, help="upstream release tag, e.g. v2.0.62")
    parser.add_argument("--out", required=True, type=Path, help="project root")
    args = parser.parse_args()

    source = args.upstream / "src"
    ifsc_data = json.loads((source / "IFSC.json").read_text(encoding="utf-8"))
    bank_names = json.loads((source / "banknames.json").read_text(encoding="utf-8"))
    banks = json.loads((source / "banks.json").read_text(encoding="utf-8"))
    sublets = json.loads((source / "sublet.json").read_text(encoding="utf-8"))
    custom_sublets = json.loads(
        (source / "custom-sublets.json").read_text(encoding="utf-8")
    )

    validation_codes = expand_ifsc_codes(ifsc_data)
    branches = load_branch_database(args.by_bank)

    missing_from_release = validation_codes - branches.keys()
    missing_from_validation = branches.keys() - validation_codes
    if missing_from_release or missing_from_validation:
        fail(
            "validation and release datasets disagree: "
            f"{len(missing_from_release)} only in IFSC.json, "
            f"{len(missing_from_validation)} only in by-bank"
        )

    missing_names = sorted({code[:4] for code in validation_codes} - bank_names.keys())
    if missing_names:
        print(
            "warning: no display name for "
            f"{len(missing_names)} bank code(s): {', '.join(missing_names)}",
            file=sys.stderr,
        )

    for code, target in sublets.items():
        if len(code) != 11 or len(target) != 4:
            fail(f"invalid sublet entry: {code!r} -> {target!r}")

    stale_prefixes = [
        prefix
        for prefix in custom_sublets
        if not any(code.startswith(prefix) for code in validation_codes)
    ]
    if stale_prefixes:
        print(
            f"warning: {len(stale_prefixes)} custom sublet prefix(es) do not match "
            f"any current IFSC: {', '.join(sorted(stale_prefixes)[:8])}"
            + ("..." if len(stale_prefixes) > 8 else ""),
            file=sys.stderr,
        )

    generated_at = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%d")
    out = args.out

    write_core_binary(
        out / "core/src/main/resources/dev/ifsc/ifsc-core.bin",
        args.version,
        ifsc_data,
        validation_codes,
        bank_names,
        banks,
        sublets,
        custom_sublets,
    )

    create_branch_database(
        out / "offline/src/main/assets/ifsc.db",
        branches,
        {
            "data_version": args.version,
            "generated_at": generated_at,
            "upstream": "https://github.com/razorpay/ifsc",
            "branch_count": str(len(branches)),
            "bank_count": str(len({code[:4] for code in branches})),
        },
    )

    write_generated_kotlin(
        out / "offline/src/main/kotlin/dev/ifsc/offline/IfscOfflineData.kt",
        args.version,
        generated_at,
        len(branches),
        len({code[:4] for code in branches}),
    )

    fixture_source = args.upstream / "tests/validator_asserts.json"
    fixture_target = out / "core/src/test/resources/validator_asserts.json"
    fixture_target.parent.mkdir(parents=True, exist_ok=True)
    fixture_target.write_text(fixture_source.read_text(encoding="utf-8"), encoding="utf-8")

    print(f"generated dataset {args.version} ({generated_at})")


if __name__ == "__main__":
    main()
