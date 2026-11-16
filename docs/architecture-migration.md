# Incremental Android architecture migration

## Non-negotiable compatibility boundary

Keep the existing Android View hierarchy, resources, navigation, animations, and drawing code as the presentation layer. A migration changes implementation behind existing Java-facing APIs; it must not replace the chat UI or alter message layout, colors, typography, touch targets, or animation timing without a separate visual review.

Do not bulk-translate `ChatMessageCell`, `MessageObject`, `MessagesController`, or the storage/network stack. Migrate one bounded responsibility at a time, with the old implementation available for comparison until correctness and performance are demonstrated.

## Language responsibilities

- **Java (existing compatibility layer):** Android framework callbacks and stable APIs used throughout the application. Avoid broad rewrites.
- **Kotlin:** new lifecycle-aware orchestration and immutable UI/domain-facing models. Kotlin is not presumed faster than Java; its purpose is safer boundaries and easier structured concurrency. Coroutines may be introduced only with explicit cancellation and dispatcher ownership.
- **C++20:** existing latency-sensitive native transport, codecs, and storage primitives. Add JNI only for measured hotspots; do not marshal per-frame values or individual chat rows through JNI.
- **Rust:** isolated deterministic parsers and security-sensitive transformations with explicit input/output buffers. Reuse the repository's Rust build/integration approach; no Rust dependency should leak into Android UI code.

## Migration sequence and gates

1. **Stabilize inputs and behavior.** Keep intent parsing, WebView bridge validation, push processing, and network recovery behind bounded APIs. Run the Java compile check and full `assembleAfatRelease` Actions workflow for each source change.
2. **Measure UI before optimizing it.** Record frame timing and allocation/GC traces for representative chat scenarios on at least one 60 Hz and one high-refresh device. Identify allocations by call stack; do not claim zero-GC based on source-level searches alone.
3. **Remove verified repeated UI allocations.** Preserve the current drawing implementation and introduce reusable state only where traces show a per-frame allocation. Never share mutable `Paint`, `Rect`, layout, or canvas state between views/threads.
4. **Move pure message processing off the UI thread.** Define immutable request/result objects, cancellation keyed to message identity, and stale-result rejection. Keep Android `StaticLayout` creation on a supported thread and confirm platform/API behavior before moving it.
5. **Native storage experiment.** Prototype a single read-only query using prepared SQLite statements and a versioned flat-buffer contract. Benchmark against the current `MessagesStorage` implementation; retain the old path until migrations, corruption handling, and performance tests pass. Never put database keys or Telegram auth material in logs or unmanaged buffers.
6. **Rust only for bounded pure work.** Add an FFI adapter with length-checked inputs, explicit ownership, and fuzz/property tests. Keep it out of synchronous draw/layout paths.
7. **Rollout gate.** Each migrated slice must pass release APK Actions, regression tests, and before/after measurements. Revert the slice if correctness, frame time, allocations, battery use, or memory regress.

## Current starting point

The repository already builds its native transport and SQLite as C/C++ and has a Rust wallet engine integrated through a generated C++ wrapper. These are existing subsystems, not evidence that chat rendering or message storage has already migrated. The first chat-rendering work should therefore be measurement-led and leave the current UI architecture intact.
