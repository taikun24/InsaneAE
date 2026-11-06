# ACO PR 下書き: 容量上限 API / AEKey 台帳 API を mc/1.21.1 へ移植

branch: `port/capacity-api-1211` (base `mc/1.21.1`)
cherry-pick: `70ecfbd feat(api): expose BigInteger limits and AEKey ledger`

---

## Summary

Ports `70ecfbd` ("feat(api): expose BigInteger limits and AEKey ledger") from the
1.20.1 line to `mc/1.21.1`. That commit is currently 1.20.1-only, so the two
public surfaces it adds are missing on 1.21.1:

- `CAPACITY_LIMIT_API_VERSION` / `maximumSupportedAmount()`
- `AMOUNT_LEDGER_API_VERSION` 2 with `createAeKeyAmountLedger()` /
  `loadAeKeyAmountLedger()`

## Why

Add-ons that size a crafting CPU from ACO's configured limit have no way to read
it on 1.21.1. InsaneAE's BigInteger crafting storage does exactly this: it
reflects `maximumSupportedAmount()` and, when the lookup fails, falls back to a
`Long.MAX_VALUE` per-block capacity. On 1.21.1 that fallback is always taken, so
the block silently becomes an ordinary 9.22e18-byte crafting storage and wide
orders are refused with `CPU_TOO_SMALL` — with a stock ACO 1.5.24 + the
`bigIntegerMaximumBits = 54427` default, the intended capacity is 10^16384 - 1
bytes per block. Same story for the ledger: the v2 AEKey factories are what let
an integration avoid naming ACO's internal codec type, so add-ons on 1.21.1 fall
back to a local ledger.

Both branches already carry everything the commit needs
(`AeKeyBigCraftingCodec`, `BigCountMath`), so this is a straight port.

## Changes

- `BigCraftingEngineApi`: adds the capacity-limit query and the AEKey ledger
  factories, bumps `AMOUNT_LEDGER_API_VERSION` to 2 and adds
  `CAPACITY_LIMIT_API_VERSION` 1. `EXTERNAL_CONSUMER_API_VERSION` and the
  external-consumer registration added on 1.21.1 after the original commit are
  kept as-is (the conflict was only about neighbouring constants and javadoc).
- `BigCraftingEngineApiCapacityTest`: ported unchanged.
- Docs: `EXPERIMENTAL_ENGINE.md` section and CHANGELOG entries, merged into the
  existing `[Unreleased] / Added` block.

## Verification

- `./gradlew build` on `mc/1.21.1` + this commit: all unit tests green
  (Java 21, `-PacoLocalModsDir` with the pinned 1.21.1 dependency jars).
- InsaneAE's GameTest suite on 1.21.1, 94/94 green with this jar, including a
  new plot that asserts the BigInteger crafting storage reports a capacity above
  the signed-long range. That plot fails against stock 1.5.24 and passes on the
  1.20.1 line, which is the asymmetry this PR removes.
