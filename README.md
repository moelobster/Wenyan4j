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

## Notes

- Current runtime targets practical execution of repository examples first.
- `import` and object-related statements are parsed but currently no-op in runtime.

