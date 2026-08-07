# Compose Docking

IDE-style window docking for [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/):
splits, tab groups, drag-to-dock, floating windows, auto-hide toolbars, maximize, and layout persistence.

A port of the concepts and algorithms of [ModernDocking](https://github.com/andrewauclair/ModernDocking)
(Java Swing, MIT) to an idiomatic Compose API.

**Status: early development — nothing published yet.**

## Modules

| Artifact | Description | Targets |
|---|---|---|
| `com.seanproctor:compose-docking-core` | Layout engine, state model, unstyled docking machinery | JVM (desktop), Android, iOS, wasmJs |
| `com.seanproctor:compose-docking-material3` | Material 3 renderer | same as core |
| `com.seanproctor:compose-docking-jewel` | Jewel (IntelliJ look-and-feel) renderer | JVM only, requires [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime) |

Floating OS windows and cross-window drag are desktop (JVM) features; all other functionality —
splits, tabs, in-window drag-to-dock, auto-hide, maximize, persistence — works on every target.

## Demos

```bash
./gradlew :demo:demo-material3:run   # Material 3 look
./gradlew :demo:demo-jewel:run       # IntelliJ look (run on JetBrains Runtime)
```

## Credits

The docking model — drop-region precedence, region sensitivity, anchors, auto-hide, layout
persistence semantics — is ported from [ModernDocking](https://github.com/andrewauclair/ModernDocking)
by Andrew Auclair (MIT License).

## License

[MIT](LICENSE)
