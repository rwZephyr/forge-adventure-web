# D3c launcher amendment — source review only

[Owner approval](https://github.com/rwZephyr/forge-adventure-web/pull/27#issuecomment-6275624757)
authorizes a launcher-only amendment to PR #27, not execution. No launcher body,
Java process, compilation or witness scenario was executed for this amendment.
The failed `d3c-first-1` evidence, manifest and private originals are unchanged.

## Literal entry-point matcher

The launcher now constructs the pattern with
`[regex]::Escape('experiment.BoltLifecycleMain')`. It preserves the original
case-insensitive literal-class substring check; it does not accept unknown entry
points or executables.

There is a correction to the review premise: character-code inspection of the
actual launcher at `099b46946af2b7b31e9d3597c6b7ad858635577d` shows **one**
U+005C backslash immediately before U+002E dot. Its SHA-256 is the historical
manifest's `b5aeddf863dc9149b6e7304a819fd38ee1871af8e2038aeacf2ff76892b3e625`.
An isolated static regex expression confirms that historical pattern already
matches `experiment.BoltLifecycleMain`. JSON/Markdown escaping is not a count of
source characters. Using `Escape` removes manual-escaping ambiguity, but this
change **does not establish or fix the original run's cause**. The actual CIM
values were not retained in that attempt.

## Separate, bounded diagnostics

On a future separately approved execution, `Record-ChildValidation` will append
`child-validation.jsonl` records for each newly inspected candidate and the
retained/rechecked child. Creation-time and parent rejections also record their
own stage before throwing. The fields distinguish:

- CIM record availability, observed PID/parent PID and expected parent PID.
- Handle start UTC and creation-time verdict (null before handle observation).
- Executable-path presence, character count and exact expected-path verdict.
- Command-line presence, character count and literal entry-point verdict.

Privacy uses an allowlist rather than copying arbitrary command-line prefixes:
the path summary is `<JDK>/bin/java.exe` only on an exact expected-path match;
otherwise it is `<redacted-unrecognized-path>`. Observed command-line markers
are at most the four fixed strings `java.exe`, `-agentlib:jdwp`,
`transport=dt_shmem`, and `experiment.BoltLifecycleMain`, included only when
present. All other path/argument content is omitted, including unexpected
children's possible credentials or user directories. Presence/count/verdict
fields remain useful when CIM fields are null or an identity check fails.
No raw CIM path or command line is written, even to the isolated output directory.
There are no runtime diagnostic samples from this source-only amendment.

The diagnostic result supplies the **same separate executable and entry-point
verdicts** used by the fail-closed guard. Parent recheck, retained process handles,
creation-time threshold, expected executable equality, live-exit checks, nested
deadlines and owned-process termination remain in place. Only the diagnostic
record and literal-pattern construction change; rejection still halts the run.
Diagnostic I/O failure also propagates through the existing process cleanup.
No retry, fallback executable, new transport or gameplay cleanup is introduced.

## Static verification

PowerShell's `Parser.ParseFile` reported **zero errors**, without executing the
script body. Isolated regex expressions (not launcher/helper invocation) verified
that the `Escape` pattern accepts the dotted class and rejects a wildcard-dot
substitute and a backslash-class substitute. The historical pattern's source
character count and matching result were inspected separately as described above.
`git diff --check` passed. Review confirmed that only the launcher and these
status/design notes changed; Java sources and original gameplay are unchanged.
Historical evidence file hashes remain valid.

This is a source-review gate. Runtime diagnostics, process behavior and D3c
ordering remain unverified by this amendment. Another single run requires a
fresh explicit owner decision after review. No run is requested or started here.
