# Wenyan4j

A minimal `wenyan-lang` runtime in Java.

## Features

- Parse Wenyan source via ANTLR (`src/main/antlr/wenyan.g4`)
- Execute core statements used by examples:
  - variable declaration / naming
  - arithmetic (`加` `減` `乘` `除` `所餘幾何`)
  - conditionals (`若` / `若非`)
  - loops (`為是...遍`, `恆為是`, `凡...中之`)
  - arrays (`充`, `銜`, `夫...之...`, `...之長`)
  - function define / call / return
  - assignment (`昔之...者今...是矣`)
  - print (`書之`)

## Quick Start

```powershell
./gradlew.bat run --args="example/天地，好在否.wy"
```

Run another example:

```powershell
./gradlew.bat run --args="example/乘算口訣.wy"
```

## Test

```powershell
./gradlew.bat test
```

## Build Runnable Jar (Shadow)

```powershell
./gradlew.bat shadowJar
java -jar build/libs/Wenyan4j-1.0.0-all.jar example/天地，好在否.wy
```

The shaded jar is built with dependency minimization (`minimize()`), so unused dependency classes are excluded.

## Notes

- Current runtime targets practical execution of repository examples first.
- `吾嘗觀...之書` now supports annotated Wenyuan pavilions (see `src/main/java/dev/anvilcraft/base/wenyan/wenyuan`).
- Built-in simplified mode is opt-in: if the file starts with `吾嘗觀『简化秘术』之書` (or `吾尝观『简化秘术』之书`), simplified Wenyan keywords/literals are enabled; otherwise only traditional forms are accepted.
- `@WenyuanPavilion` can be declared on `package-info.java`; then all `public static` methods annotated with `@WenyuanFunction` in that package are auto-registered.
- Imported Java return objects can expose Wenyan fields via `@WenyuanField` (e.g. `其之『商』`, `其之『餘』`).
- Extension users can register extra packages/classes with `WenyanEngine.registerWenyuanPackage(...)` and `WenyanEngine.registerWenyuanClass(...)`.
- If you add reflection-based libraries in the future, configure Shadow excludes to avoid over-minimization.

