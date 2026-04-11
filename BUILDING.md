# Building Mini Quarkus

This repo follows a simplified Quarkus-inspired build workflow, but it is now moving toward fully custom `com.tanwir` modules.

## Environment

Before any build:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env
source ./env.build.sh
```

This activates:

- Java `17.0.16-tem`
- `mvnd` `1.0.3`
- `MAVEN_OPTS="-Xmx4g -XX:MaxMetaspaceSize=1g"`
- `JAVA_TOOL_OPTIONS="-Xmx4g"`

## Commands That Work In This Repo

Full build:

```bash
./mvnw clean install
```

or

```bash
mvnd clean install
```

Fast build:

```bash
./mvnw -Dquickly
```

or

```bash
mvnd clean install -Dquickly
```

Verbose equivalent of `-Dquickly`:

```bash
mvnd clean install -DskipTests -DskipITs -Dinvoker.skip -Dno-format -Dno-native
```

## What `-Dquickly` Means

The root `pom.xml` defines a `quick-build` profile that activates when `-Dquickly` is present.

That profile sets these properties to `true`:

- `skipTests`
- `skipITs`
- `invoker.skip`
- `no-format`
- `no-native`
- `maven.javadoc.skip`
- `maven.source.skip`
- `checkstyle.skip`

## Module-Only Build

This repo now has a real module path under `extensions/`.

Build only the ARC extension and whatever reactor modules it depends on:

```bash
./mvnw install -pl extensions/arc -am
```

`-pl` selects the project list and `-am` also builds required reactor modules.

## Custom Modules Pending

The following pieces are intentionally left as placeholders until you implement your own `com.tanwir` modules:

- runtime modules in the application BOM
- framework dependencies in `integration-tests/main`
- a custom build/dev plugin
- native-image support

For now the repo keeps building by not depending on upstream `io.quarkus` artifacts.

## Recommended Strategy For This Repo

Use this repo in three stages:

1. Build the current reactor with `mvnd clean install`
2. Iterate quickly with `mvnd clean install -Dquickly`
3. Add real `com.tanwir` modules and plugin wiring before introducing custom dev mode or native builds
