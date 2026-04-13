# mini-quarkus

This is a minimal Quarkus-inspired repo for understanding the project structure and build flow.

The first build-time ArC slice now lives in:

- `independent-projects/arc/runtime`: tiny CDI-like runtime container
- `independent-projects/arc/processor`: compile-time bean discovery and generated wiring
- `independent-projects/bootstrap/app-model`: generated application model loader
- `independent-projects/bootstrap/runner`: packaged application entrypoint
- `independent-projects/resteasy-reactive/server/runtime`: Vert.x-based GET runtime
- `independent-projects/resteasy-reactive/server/processor`: compile-time GET route generation
- `integration-tests/main`: sample application that uses generated bean and route bootstrap code

If you want to inspect the generated code, build `integration-tests/main` and then look under:

- `integration-tests/main/target/generated-sources/annotations`
- `integration-tests/main/target/classes/META-INF/services`
- `integration-tests/main/target/classes/META-INF/mini-quarkus/application-model.properties`

To package and run the sample app end to end:

```bash
./mvnw package -pl integration-tests/main -am
java -jar integration-tests/main/target/mini-quarkus-main.jar
```

Then open:

```text
http://localhost:8080/hello
```

There is also a helper script:

```bash
./scripts/run-main-app.sh
```

The packaged jar now boots through the bootstrap runner, not an application-specific `main()` method.

Build instructions are in [BUILDING.md](./BUILDING.md).
