# kmp-lsp test project

Sample workspace for testing the **KMP LSP** plugin. Nothing here needs to be compiled — the
point of kmp-lsp is that navigation works in a project Gradle has never imported.

```
test-project/
├── settings.gradle.kts        workspace-root marker (kmp-lsp uses it to find the root)
├── build.gradle.kts
└── src/main/
    ├── kotlin/com/example/kmptest/
    │   ├── Main.kt            ← open this one first
    │   ├── Models.kt          enum, data class, sealed interface, typealias, object
    │   ├── Repository.kt      interface + 2 implementations + inherited default method
    │   ├── Extensions.kt      extension functions, star import, named/default arguments
    │   ├── Delegation.kt      by lazy, Delegates.observable, hand-written delegate, `by`
    │   ├── Interop.kt         @JvmStatic / @JvmOverloads entry points for the Java file
    │   └── util/Strings.kt    second package, reached through a star import
    ├── java/com/example/kmptest/LegacyClient.java
    └── swift/Greeter.swift
```

## Checklist

Open `Main.kt` and walk down the table. `Ctrl/Cmd`-click, or whatever your editor binds
go-to-definition to.

| # | Feature | Where | What to do | Expected |
|---|---------|-------|------------|----------|
| 1 | go-to-definition, cross-file | `Main.kt` → `repository.save(it)` | jump on `save` | `Repository.kt`, `save` |
| 2 | inherited member | `Main.kt` → `repository.count()` | jump on `count` | `Repository.kt` → `fun count(): Int = all().size` (declared on the interface, implemented nowhere) |
| 3 | superclass hierarchy | `CachingRepository.findById` → `store.findById` | jump on `findById` | resolves through the `by store` delegation |
| 4 | hover | any KDoc'd symbol, e.g. `Priority` | hover | KDoc text + declaration line |
| 5 | signature help | `Main.kt` inside `it.render(` | — | active parameter highlighted while typing `,` |
| 6 | named + default args | `it.render(showTags = false, prefix = "! ")` | hover `render` | both parameters and their defaults |
| 7 | dot completion | type `repository.` or `it.` | — | members of the declared type; `private` members hidden |
| 8 | inlay hints | `forEach { println(it.render()) }` | — | a type hint on `it` |
| 9 | semantic tokens | whole file | — | parameters / decorators / functions coloured |
| 10 | references | `Task` or `isOverdue` | find references | hits across `Models.kt`, `Main.kt`, `Repository.kt`, `LegacyClient.java` |
| 11 | rename | `highPriority` | rename | updates `Repository.kt` + `Main.kt` together |
| 12 | implementations | `Repository` | go to implementations | `InMemoryTaskRepository`, `ReadOnlyTaskRepository`, `CachingRepository` |
| 13 | star import | `Extensions.kt` → `titleCase()` | jump on `titleCase` | `util/Strings.kt` |
| 14 | extension on stdlib | `Extensions.kt` → `String.quote` | hover / completion on a `String` | project extension ranks before stdlib entries |
| 15 | delegation | `Delegation.kt` → `by lazy`, `by Delegates.observable`, `by Counting()` | hover `lazy` / jump on `Counting` | stdlib + the local class |
| 16 | enum + sealed | `Models.kt` → `Priority.URGENT`, `TaskResult` | jump / completion | enum entries, sealed subtypes |
| 17 | typealias | `TaskList` used in `Repository.kt` | jump on it | `Models.kt` |
| 18 | Kotlin ↔ Java | `LegacyClient.java` → `LegacyBridge.describe` | jump | `Interop.kt` (and back: `client.send(` in `Main.kt` → the Java method) |
| 19 | Swift | `Greeter.swift` | hover, jump, completion on `greeter.` | struct/enum/protocol/extension/typealias members |
| 20 | `.kts` | `build.gradle.kts` | open it | same server, `.kts` is registered too |

## If something looks empty

- **No library/stdlib entries in completion.** Expected without a Gradle cache: kmp-lsp reads
  `*-sources.jar` and compiled jars from `~/.gradle/caches`, plus the Android SDK via
  `local.properties`. Kotlin stdlib signatures are built in, so `let`/`apply`/`map` should still
  show up; Compose and AndroidX members will not, until one Gradle sync has run on the device.
- **Nothing at all.** Check the wrapper first: `ls -l /usr/local/bin/kmp-lsp` in the terminal, and
  `kmp-lsp-debug on` before opening a file, then read `/tmp/kmp-lsp.log`.
- **A `.kt` file is served by a different server.** The KMP LSP provider registers with priority
  200 so it wins over the Kotlin/Java LSP plugins in the store (they register with 0). If Kotlin
  highlighting works but completion/hover do not, check the log for which executable was launched.
