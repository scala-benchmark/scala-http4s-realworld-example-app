# Build Documentation

> For the full, comprehensive guide (running the app, DB/Flyway, strict compiler flags,
> troubleshooting), see **`how-to-compile.md`**. This file records the **exact procedure
> that actually worked on our Windows machine**.

This project failed to run under WSL, so the build was done in a Windows environment.

## Critical correction: use JDK 11, not JDK 17

A previous version of this document said to install **Java 17**. That does **not**
compile this project. The Scala 2.12.8 toolchain breaks on modern JDKs:

- **JDK 21**: sbt does not even launch:
  `java.lang.ClassCastException: ... UnsupportedOperationException cannot be cast to xsbti.FullReload`
- **JDK 17**: sbt launches but compilation crashes during macro expansion:
  `java.io.IOError: java.lang.RuntimeException: /packages cannot be represented as URI`
  (passing `--add-opens` flags does not help)
- **JDK 11**: compiles cleanly. **This is what you must use** (JDK 8 also works).

## The exact solution we used on this machine

Our machine only had JDK 17 and 21 installed (both fail, see above). We did **not**
uninstall them. Instead we downloaded a **portable JDK 11**, pointed `JAVA_HOME` at it
for the build session, and compiled. No admin rights or system changes were needed.

### 1. Download and extract a portable Temurin JDK 11 (PowerShell)

```powershell
$dir = "C:\Users\vitor\jdk11-temp"
New-Item -ItemType Directory -Force $dir | Out-Null
$url = "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.25%2B9/OpenJDK11U-jdk_x64_windows_hotspot_11.0.25_9.zip"
Invoke-WebRequest -Uri $url -OutFile "$dir\jdk11.zip"
Expand-Archive -Path "$dir\jdk11.zip" -DestinationPath $dir -Force
```

This produced the JDK at: `C:\Users\vitor\jdk11-temp\jdk-11.0.25+9`

### 2. Point JAVA_HOME at JDK 11 for the build session

```powershell
$env:JAVA_HOME = "C:\Users\vitor\jdk11-temp\jdk-11.0.25+9"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version    # must print: openjdk version "11.0.25"
```

### 3. Compile

```powershell
sbt compile
```

Result: `[success] Total time: ...`

Two messages are expected and harmless:
- `method in ... is deprecated (since 1.5.0)` in `project/Common.scala`
- `WARNING: An illegal reflective access operation has occurred ... sbt-dotenv`

### Notes

- `sbt` itself is pinned to `1.5.0` via `project/build.properties`; the launcher
  bootstraps it automatically, so the sbt version on PATH does not matter.
- `JAVA_HOME` here is set **per terminal session**, so other projects keep using your
  default JDK. To make it permanent, see Option B/C in `how-to-compile.md`.
- The compiler runs with strict `sbt-tpolecat` flags (`-Xfatal-warnings`), so any
  warning (unused import, unused val, value discard, non-exhaustive match, ...) fails
  the build. Details in `how-to-compile.md`.

## Installing sbt (if not already present)

The `sbt` launcher just needs to be on PATH; it will fetch the pinned sbt 1.5.0.

Windows:

```powershell
winget install sbt.sbt
# or:  choco install sbt
```

Debian/Ubuntu:

```bash
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo tee /etc/apt/trusted.gpg.d/sbt.asc
sudo apt-get update && sudo apt-get install sbt
sbt --version
```

> On Debian/Ubuntu also install JDK 11 (`sudo apt-get install -y openjdk-11-jdk`) and
> set `JAVA_HOME` to it before building, NOT Java 17.

## Verify the build

```powershell
java -version    # 11.x
sbt -version
sbt clean compile
```

For everything else (running the app with PostgreSQL + Flyway, environment variables,
dependency cache, `javap` inspection, and a full troubleshooting table), see
**`how-to-compile.md`**.
