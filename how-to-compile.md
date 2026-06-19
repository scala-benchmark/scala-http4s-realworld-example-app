# How to compile (and run) this project

A complete, battle-tested guide. The single most important thing is the **JDK
version** (use **JDK 11**). If anything goes wrong, jump straight to
[Troubleshooting](#troubleshooting).

> Note: there is an older `Build_documentation.md` in this repo that says "install
> Java 17". **Do not follow that for compiling.** In our testing JDK 17 fails at the
> Scala macro phase (see the table below). Use **JDK 11**.

## Toolchain at a glance

| Component | Version | Pinned in |
| --- | --- | --- |
| Scala | 2.12.8 | `project/Common.scala` (`scalaLibVersion`) |
| sbt | 1.5.0 | `project/build.properties` |
| JDK to build | **11** (8 works too; 17/21 do **not**) | not pinned, you set `JAVA_HOME` |
| Build version | `1.0.0-SNAPSHOT` | `VERSION.txt` (read at build time) |
| Compiler flags | strict, via `sbt-tpolecat` | `project/plugins.sbt` |

---

## TL;DR

1. Point `JAVA_HOME` at a **JDK 11**.
2. `sbt compile`.

```powershell
# PowerShell
$env:JAVA_HOME = "C:\path\to\jdk-11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version      # MUST print 11.x
sbt compile
```

```bash
# bash / git-bash / Linux / macOS
export JAVA_HOME="/path/to/jdk-11"
export PATH="$JAVA_HOME/bin:$PATH"
java -version      # MUST print 11.x
sbt compile
```

A clean build ends with `[success] Total time: ...`.
These messages are **expected and harmless**:

- `method in ... is deprecated (since 1.5.0)` in `project/Common.scala`.
- `WARNING: An illegal reflective access operation has occurred ... sbt-dotenv`.

---

## Why JDK 11 specifically

The Scala 2.12.8 compiler and its macro machinery (circe, doobie, slick, ...) predate
modern JDKs:

| JDK | Result | Exact failure |
| --- | --- | --- |
| **8** | Compiles | , |
| **11** | Compiles (recommended, verified) | , |
| **17** | **Fails at compile** | `java.io.IOError: java.lang.RuntimeException: /packages cannot be represented as URI` during macro expansion. `--add-opens` does **not** fix it. |
| **21** | **sbt will not launch** | `java.lang.ClassCastException: ... UnsupportedOperationException cannot be cast to xsbti.FullReload` |

You do **not** need to uninstall other JDKs. Just override `JAVA_HOME` for this
project's terminal session (see [Getting JDK 11](#getting-jdk-11)).

`sbt` is pinned to `1.5.0` in `project/build.properties`. The `sbt` launcher reads that
and bootstraps the correct sbt automatically, so the launcher version on your PATH does
not matter.

---

## Getting JDK 11

### Option A: Portable JDK (no admin, no system change)

```powershell
# PowerShell
$dir = "$env:USERPROFILE\jdk11"
New-Item -ItemType Directory -Force $dir | Out-Null
$url = "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.25%2B9/OpenJDK11U-jdk_x64_windows_hotspot_11.0.25_9.zip"
Invoke-WebRequest -Uri $url -OutFile "$dir\jdk11.zip"
Expand-Archive -Path "$dir\jdk11.zip" -DestinationPath $dir -Force
$env:JAVA_HOME = "$dir\jdk-11.0.25+9"   # the extracted folder name
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
```

```bash
# bash / Linux / macOS (Temurin example)
mkdir -p ~/jdk11 && cd ~/jdk11
curl -L -o jdk11.tar.gz "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.25%2B9/OpenJDK11U-jdk_x64_linux_hotspot_11.0.25_9.tar.gz"
tar xzf jdk11.tar.gz
export JAVA_HOME="$PWD/jdk-11.0.25+9"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

### Option B: System install

- Download: <https://adoptium.net/temurin/releases/?version=11>
- Windows winget: `winget install EclipseAdoptium.Temurin.11.JDK`
- Windows Chocolatey: `choco install temurin11`
- Debian/Ubuntu: `sudo apt-get install -y openjdk-11-jdk`
- macOS Homebrew: `brew install temurin@11`

Make `JAVA_HOME` permanent for your user (open a **new** terminal afterwards):

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-11.0.25.9-hotspot", "User")
```

### Option C: Per-project wrapper (keep JDK 11 scoped to this repo)

If your machine default must stay on 17/21, drop a tiny wrapper at the repo root so you
never forget to switch:

```powershell
# build.ps1   ->   .\build.ps1 compile   /   .\build.ps1 test
$env:JAVA_HOME = "$env:USERPROFILE\jdk11\jdk-11.0.25+9"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
sbt @args
```

```bash
# build.sh   ->   ./build.sh compile
#!/usr/bin/env bash
export JAVA_HOME="$HOME/jdk11/jdk-11.0.25+9"
export PATH="$JAVA_HOME/bin:$PATH"
exec sbt "$@"
```

---

## Prerequisites checklist

- [ ] **JDK 11** reachable via `JAVA_HOME` (`java -version` prints 11.x in the build shell).
- [ ] **sbt launcher** on PATH (`sbt -version` runs; it bootstraps sbt 1.5.0).
- [ ] **git** (repo cloned).
- [ ] **Internet** for the first build (downloads compiler + deps into the Coursier cache).
- [ ] **`VERSION.txt`** present at repo root (the build reads it; currently `1.0.0-SNAPSHOT`).
- [ ] **`.env`** present at repo root (loaded by `sbt-dotenv`; currently `LOG_HTTP=true`).
- [ ] **PostgreSQL** only if you want to **run** the app or run DB-backed tests (not needed for `sbt compile`).

---

## sbt plugins in this build (context)

From `project/plugins.sbt`:

| Plugin | Purpose | Tasks you may use |
| --- | --- | --- |
| `sbt-dotenv` | Loads `.env` into the JVM env on startup | (automatic) |
| `sbt-tpolecat` | Strict Scala compiler flags (see next section) | (automatic) |
| `flyway-sbt` | Database migrations | `flywayMigrate`, `flywayClean`, `flywayInfo` |
| `sbt-revolver` | Hot app restart in the background | `reStart`, `reStop` |
| `sbt-native-packager` | Build distributable packages | `stage`, `Universal/packageBin`, `Docker/publishLocal` |
| `sbt-updates` | Check for newer dependency versions | `dependencyUpdates` |

---

## IMPORTANT: the compiler is strict (sbt-tpolecat)

`sbt-tpolecat` turns on `-Xfatal-warnings` plus a large set of lints. **Warnings fail
the build.** The full active set (from `sbt "show scalacOptions"`):

```
-deprecation -feature -unchecked -explaintypes -Xcheckinit -Xfatal-warnings
-Yno-adapted-args -Ywarn-dead-code -Ywarn-extra-implicit -Ywarn-numeric-widen
-Ywarn-value-discard -Ypartial-unification
-Ywarn-unused:implicits -Ywarn-unused:imports -Ywarn-unused:locals
-Ywarn-unused:params -Ywarn-unused:patvars -Ywarn-unused:privates
-Xlint:adapted-args,by-name-right-associative,constant,delayedinit-select,doc-detached,
       inaccessible,infer-any,missing-interpolator,nullary-override,nullary-unit,
       option-implicit,package-object-classes,poly-implicit-overload,private-shadow,
       stars-align,type-parameter-shadow,unsound-match
```

Practical consequences when you edit code:

| If you ... | You get a fatal error | Fix |
| --- | --- | --- |
| Leave an **unused import** | `Unused import` | Remove it |
| Leave an **unused** local/private val or param | `... is never used` | Remove it, or use it, or name it `_` |
| **Discard a non-Unit value** where Unit is expected | value-discard warning | End the block with `()`, or `val _ = expr` |
| Write a **non-exhaustive match** | `match may not be exhaustive` | Cover all cases |
| Let a type **infer to `Any`** | `Xlint:infer-any` | Add an explicit type |
| Leave **dead code** | `Ywarn-dead-code` | Remove it |

Tip: a single real error often triggers a **cascade** of follow-on warnings (e.g. a
private val reported "never used" only because the method that used it failed to
compile). Always fix the **first** `[error]`, then recompile.

---

## Common commands

```bash
sbt compile                                        # compile main sources
sbt Test/compile                                   # compile tests
sbt test                                           # run all tests
sbt "testOnly *AuthRoutesSpec"                     # run a single spec
sbt clean                                          # delete target/
sbt clean compile                                  # full rebuild
sbt update                                         # resolve/download deps only
sbt "show scalacOptions"                           # print active compiler flags
sbt dependencyUpdates                              # check for newer dep versions
sbt reStart                                        # start the app in background (sbt-revolver)
sbt reStop                                         # stop it
sbt "runMain com.hhandoko.realworld.Application"   # run in foreground (needs Postgres)
```

### Fastest edit loop: the sbt shell

Each `sbt <task>` invocation boots a fresh JVM (slow). Open the shell once:

```
sbt
> compile
> ~compile        # watch mode: recompile on every save (best feedback loop)
> ~testOnly *AuthRoutesSpec
> exit
```

---

## Running the app (needs PostgreSQL)

`sbt compile` does **not** need a database. **Running** the app (or DB-backed tests)
does. Defaults come from `src/main/resources/reference.conf` and can be overridden by
env vars via `application.conf`.

Default DB config (`reference.conf`):

```
db.url      = jdbc:postgresql://0.0.0.0:5432/postgres
db.user     = postgres
db.password = S3cret!
db.pool     = 5
server       = 0.0.0.0:8080
```

### 1. Start PostgreSQL (docker-compose ships one)

```bash
docker compose up -d db      # postgres:13-alpine on localhost:5432, password S3cret!
```

(If you do not use Docker, install Postgres 13+, create the `postgres` DB, and set the
password to `S3cret!`, or override the env vars below.)

### 2. Apply DB migrations (Flyway)

Flyway is configured in `project/Common.scala` to point at the same DB
(`jdbc:postgresql://0.0.0.0:5432/postgres`, user `postgres`, password `S3cret!`) and to
load from `db/migration/postgresql` and `db/seed`.

```bash
sbt flywayMigrate     # create schema + seed data
sbt flywayInfo        # show migration status
```

### 3. Run

```bash
sbt "runMain com.hhandoko.realworld.Application"
# or, with auto-restart on changes:
sbt reStart
```

The API then listens on `http://localhost:8080`.

### Environment variables (override config without editing files)

`application.conf` maps these env vars onto config keys (see `.env` for local defaults):

| Env var | Config key | Default (reference.conf) |
| --- | --- | --- |
| `APP_HOST` | `server.host` | `0.0.0.0` |
| `APP_PORT` | `server.port` | `8080` |
| `DB_DRIVER` | `db.driver` | `org.postgresql.Driver` |
| `DB_URL` | `db.url` | `jdbc:postgresql://0.0.0.0:5432/postgres` |
| `DB_USER` | `db.user` | `postgres` |
| `DB_PASSWORD` | `db.password` | `S3cret!` |
| `DB_POOL` | `db.pool` | `5` |
| `LOG_HTTP` | `log.http-header` / `log.http-body` | `false` (`.env` sets it `true`) |

---

## How dependencies are resolved

- Libraries are declared in **`project/Common.scala`** (`dependencySettings`), **not** in
  `build.sbt`. Add new deps there, then run `sbt update` (or just `sbt compile`).
- They are cached in the **Coursier cache**:
  - Windows: `%LOCALAPPDATA%\Coursier\Cache\v1\`
  - Linux: `~/.cache/coursier/v1/`
  - macOS: `~/Library/Caches/Coursier/v1/`
- To recover from a corrupt download: delete the relevant artifact folder under that
  cache and rerun `sbt update`.

### Inspecting a library's real API with javap

When you need the exact constructor/method signature of a dependency (no docs handy):

```bash
# 1. locate the jar in the cache
ls "$LOCALAPPDATA/Coursier/Cache/v1/https/repo1.maven.org/maven2/<group/as/path>/<artifact>/<version>/"*.jar
# 2. dump the public API with the JDK 11 javap
"$JAVA_HOME/bin/javap" -cp "<path-to.jar>" fully.qualified.ClassName
```

For a Scala case class, the `javap` accessor names equal the `apply(...)` / `copy(...)`
parameter names, so this is the reliable way to get named-argument names right.

---

## Environment variables and JVM flags (gotchas)

- **Choosing the JDK**: sbt uses `JAVA_HOME` if set, else the first `java` on PATH.
  Always confirm with `java -version` in the **same** shell. Wrong default JDK is the
  number-one failure here.
- **`JAVA_TOOL_OPTIONS`** is read directly by the JVM and is the reliable way to pass
  extra JVM flags (`-Xmx`, `--add-opens`, ...). On this toolchain `--add-opens` does
  **not** rescue JDK 17.
- **`SBT_OPTS` with a `-J` prefix fails** under the wrapper launcher
  (`Unrecognized option: -J--add-opens...`). Use `JAVA_TOOL_OPTIONS` instead.
- **Out of memory**: `JAVA_TOOL_OPTIONS="-Xmx2g"` then rebuild.

---

## Windows-specific notes

- git-bash uses POSIX paths (`/c/Users/...`); PowerShell uses `C:\Users\...`. Both work,
  just stay consistent within one command.
- `git add` may warn `LF will be replaced by CRLF` , harmless line-ending normalization.
- Quote paths containing spaces: `"C:\Program Files\..."`.
- Run `sbt` from the repo root (where `build.sbt` lives).
- WSL note: per the legacy `Build_documentation.md`, this project failed to run under
  WSL; building natively on Windows (or Linux/macOS) with JDK 11 is the known-good path.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `cannot be cast to xsbti.FullReload` | Running on **JDK 21** | Point `JAVA_HOME` at JDK 11 |
| `/packages cannot be represented as URI` | Running on **JDK 17** | Point `JAVA_HOME` at JDK 11 (`--add-opens` will not help) |
| Build uses the wrong Java | `JAVA_HOME` unset / PATH order | `java -version` to confirm; put `$JAVA_HOME\bin` first |
| `Unrecognized option: -J--add-opens...` | JVM flags via `SBT_OPTS -J` | Use `JAVA_TOOL_OPTIONS` instead |
| `Unused import` / `... is never used` (as `[error]`) | `-Xfatal-warnings` + `-Ywarn-unused` | Remove the unused import/val/param (or name it `_`) |
| `... is never used` but it **is** used | **Cascade** from an earlier real error | Fix the **first** `[error]` above it, recompile |
| `match may not be exhaustive` | Non-exhaustive pattern match (fatal) | Add the missing case(s) |
| value-discard warning | Non-Unit value dropped where Unit expected | End block with `()` or `val _ = expr` |
| `not found: value <param>` on `.copy(...)`/`apply(...)` | Wrong named param for that lib version | `javap` the class to get real param names |
| `object X is not a member of package Y` | Missing dep or wrong import | Add dep in `project/Common.scala`; reload |
| `_ is already defined as value _` | Two `val _ = ...` in one block (2.12 quirk) | Use distinct names or drop the binding |
| `VERSION.txt (No such file or directory)` | `VERSION.txt` missing at root | Restore it (contents like `1.0.0-SNAPSHOT`) |
| First build hangs / very slow | Downloading compiler + deps | Wait once; later builds are cached/offline |
| `unresolved dependency` / download errors | No internet / proxy / corrupt cache | Check connectivity/proxy; clear Coursier cache; `sbt update` |
| `OutOfMemoryError` / `Metaspace` | Heap too small | `JAVA_TOOL_OPTIONS="-Xmx2g"` |
| sbt stuck on stale state | Incremental cache out of date | `sbt clean compile` |
| `Connection refused` / `FATAL: password authentication failed` at **run** | Postgres not up / wrong creds | `docker compose up -d db`; check `DB_*` env vars match |
| App starts but DB tables missing | Migrations not applied | `sbt flywayMigrate` |
| `sbt-dotenv` reflective-access WARNING | Expected on JDK 11 | Ignore |
| `method in ... is deprecated` in `Common.scala` | Expected (old sbt syntax) | Ignore |

### Still stuck? Gather this before asking for help

```bash
java -version
echo "$JAVA_HOME"        # PowerShell: echo $env:JAVA_HOME
sbt -version
```

Run `sbt clean compile` and copy the **first** `[error]` line (errors cascade, so the
first is the real cause), plus the commands above.
