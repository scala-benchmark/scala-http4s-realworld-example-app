# How to compile

This project uses an old toolchain (**Scala 2.12.8 + sbt 1.5.0**). Getting a clean
compile depends almost entirely on using the **right JDK**.

## TL;DR

- **Use JDK 11.** Not 8, not 17, not 21.
- Run `sbt compile`.

```powershell
# PowerShell
$env:JAVA_HOME = "C:\path\to\jdk-11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
sbt compile
```

```bash
# bash / git-bash
export JAVA_HOME="/c/path/to/jdk-11"
export PATH="$JAVA_HOME/bin:$PATH"
sbt compile
```

A clean build ends with `[success] Total time: ...`. The only expected warning is a
`method in ... is deprecated` in `project/Common.scala`, which is harmless.

## Why JDK 11 specifically

The Scala 2.12.8 compiler's macro machinery (used by circe, doobie, etc.) is not
compatible with modern JDKs:

- **JDK 21**: sbt 1.5.0 will not even launch. It fails with
  `ClassCastException ... cannot be cast to xsbti.FullReload`.
- **JDK 17**: sbt launches, but compilation crashes during macro expansion with
  `java.io.IOError: java.lang.RuntimeException: /packages cannot be represented as URI`.
  Passing `--add-opens` flags does **not** fix it.
- **JDK 11**: compiles cleanly. This is the supported version.
- **JDK 8**: also works for this toolchain, but JDK 11 is the recommended choice.

sbt itself is pinned to `1.5.0` in `project/build.properties`; the `sbt` launcher
downloads it automatically, so you do not need to manage the sbt version yourself.

## Getting JDK 11

### Option A: Portable JDK (no system install)

Download and extract the Temurin 11 zip anywhere, then point `JAVA_HOME` at it.

```powershell
# PowerShell
$dir = "$env:USERPROFILE\jdk11"
New-Item -ItemType Directory -Force $dir | Out-Null
$url = "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.25%2B9/OpenJDK11U-jdk_x64_windows_hotspot_11.0.25_9.zip"
Invoke-WebRequest -Uri $url -OutFile "$dir\jdk11.zip"
Expand-Archive -Path "$dir\jdk11.zip" -DestinationPath $dir -Force
# JAVA_HOME is then: $dir\jdk-11.0.25+9
```

### Option B: System install

Install Temurin 11 (or any JDK 11 distribution) system-wide:

- Download: <https://adoptium.net/temurin/releases/?version=11>
- Windows installer (winget): `winget install EclipseAdoptium.Temurin.11.JDK`
- After install, set `JAVA_HOME` to the install dir (e.g.
  `C:\Program Files\Eclipse Adoptium\jdk-11.0.x.y-hotspot`).

You can make it permanent for your user with:

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-11.0.25.9-hotspot", "User")
```

(Open a new terminal afterwards so the change takes effect.)

## Prerequisites

- **sbt launcher** on PATH (`sbt -version` should run). The build pins sbt `1.5.0`.
- A `.env` file in the project root. The build uses the `sbt-dotenv` plugin and logs
  `.env detected` on startup. On JDK 11 you will see a harmless
  `WARNING: An illegal reflective access operation has occurred` from that plugin.

## Common commands

```bash
sbt compile          # compile main sources
sbt test             # run the test suite
sbt "runMain com.hhandoko.realworld.Application"   # run the app
sbt clean compile    # clean rebuild
```

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `cannot be cast to xsbti.FullReload` | Running on JDK 21 | Switch `JAVA_HOME` to JDK 11 |
| `/packages cannot be represented as URI` | Running on JDK 17 | Switch `JAVA_HOME` to JDK 11 |
| Wrong Java picked up | `JAVA_HOME` / `PATH` order | Put `$JAVA_HOME\bin` first on PATH; verify with `java -version` |
