# ACO #125 追記コメント下書き (2026-08-21)

PR [syarukasu/ae2-crafting-optimizer#126](https://github.com/syarukasu/ae2-crafting-optimizer/pull/126)
を開いた後に issue へ貼る用。詳細 (根本原因・検証マトリクス) は PR 本文にある。

---

Root cause found, fix proposed in #126.

The stall is at the exact-storage boundary: physical exact execution moves all
boundary items (bulk input reservation and final-output delivery) exclusively
through audited exact cells, and `insertionCapacity` returns 0 for anything
that is neither the ExtendedAE Plus Infinity BigInteger cell nor an
`ExactVectorStoragePolicy` implementation. None of the setups in my 2x2 matrix
had an audited cell that accepts the final output, so every combination took
exclusive ownership and then waited forever — with no log output even with
`logVectorDiagnostics = true` (I had to build an instrumented jar to read the
waiting reason).

#126 proves the boundary route before taking ownership (falls back to a
registered external BigInteger plan consumer, or declines with a clear
reason), and adds rate-limited stall-reason logging. Verified against
InsaneAE's 93-plot GameTest suite: stock 1.5.24 stalls, with the fix all 93
pass across the delegation / decline / owned-execution outcomes.
