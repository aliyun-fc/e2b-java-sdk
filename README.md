# E2B Java SDK

Java SDK for running AI-generated code in secure, isolated cloud sandboxes — an E2B‑compatible client for the Alibaba Cloud Serverless (Function Compute) sandbox gateway.

## What is E2B?

[E2B](https://www.e2b.dev/) is open-source infrastructure that lets you run AI-generated code in secure isolated sandboxes in the cloud. This SDK is a Java client that speaks the E2B API, so you can start and control sandboxes — run commands, read/write files, use git, execute code with the Code Interpreter, build templates, pause/resume, and more — directly from the JVM.

## Run your first Sandbox

### 1. Install the SDK

Maven:

```xml
<dependency>
    <groupId>com.alibaba.serverless</groupId>
    <artifactId>e2b-java-sdk</artifactId>
    <version>1.5.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'com.alibaba.serverless:e2b-java-sdk:1.5.0'
```

Requires **Java 8+**.

### 2. Get your E2B API key

Set an environment variable with your API key:

```bash
export E2B_API_KEY=e2b_***
# Optional, when pointing at a self-hosted / regional gateway:
export E2B_DOMAIN=your-domain
export E2B_API_URL=https://api.your-domain
```

### 3. Start a sandbox and run commands

```java
import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.model.CommandResult;

ConnectionConfig config = ConnectionConfig.builder()
        .apiKey(System.getenv("E2B_API_KEY"))
        .build();

// try-with-resources kills the sandbox automatically on close()
try (Sandbox sandbox = Sandbox.create(config)) {
    CommandResult result = sandbox.getCommands().run("echo 'Hello from E2B!'");
    System.out.println(result.getStdout()); // Hello from E2B!

    sandbox.getFiles().write("/home/user/hello.txt", "Hello, world!");
    String content = sandbox.getFiles().read("/home/user/hello.txt");
    System.out.println(content); // Hello, world!
}
```

### 4. Code execution with the Code Interpreter

Execute Python/JavaScript and get rich results (stdout/stderr, values, errors) back from the in-sandbox Jupyter server:

```java
import dev.e2b.sdk.codeinterpreter.CodeInterpreter;
import dev.e2b.sdk.codeinterpreter.Execution;

try (CodeInterpreter ci = CodeInterpreter.create(config)) {
    Execution execution = ci.runCode("x = 1\nx += 1\nprint(x)");
    System.out.println(execution.getLogs().getStdout()); // [2\n]
}
```

## Features

### Sandbox lifecycle

```java
// Create from a template (default: "base")
Sandbox sandbox = Sandbox.create("python", config);

// Create with options
NewSandbox opts = NewSandbox.builder()
        .timeout(600)
        .metadata(Map.of("owner", "team-a"))
        .envVars(Map.of("MY_VAR", "value"))
        .autoPause(true)
        .build();
Sandbox sandbox = Sandbox.create("base", config, opts);

// Connect to / resume an existing sandbox
Sandbox again = Sandbox.connect(sandboxId, config);

sandbox.setTimeout(300);        // extend timeout
sandbox.getInfo();              // metadata, state, template, ...
sandbox.isRunning();            // liveness ping
sandbox.getMetrics();           // CPU / memory / disk
sandbox.getHost(3000);          // public hostname for an exposed port
sandbox.pause();                // preserve state, resume later via connect()
sandbox.kill();                 // terminate now

// Static, ID-based operations (no data-plane connection)
Sandbox.list(config);
Sandbox.kill(sandboxId, config);
Sandbox.pause(sandboxId, config);
```

### Commands

```java
Commands cmd = sandbox.getCommands();

cmd.run("ls -la");                              // foreground, waits for exit
cmd.runOrThrow("test -f /tmp/x");               // throws on non-zero exit
CommandHandle handle = cmd.runBackground("sleep 30");  // background process
handle.getPid();
handle.waitForExit();

cmd.list();                                     // running processes
cmd.sendStdin(handle.getPid(), "input\n");      // write to stdin
cmd.kill(handle.getPid());                      // terminate a process
```

### Filesystem

```java
Filesystem fs = sandbox.getFiles();

fs.write("/home/user/a.txt", "content");
fs.read("/home/user/a.txt");
fs.readBytes("/home/user/img.png");
fs.list("/home/user");
fs.exists("/home/user/a.txt");
fs.getInfo("/home/user/a.txt");
fs.rename("/home/user/a.txt", "/home/user/b.txt");
fs.makeDir("/home/user/dir");
fs.remove("/home/user/dir");
sandbox.downloadUrl("/home/user/b.txt");        // pre-signed download URL
```

### Git

```java
Git git = sandbox.getGit();
String repo = "/home/user/repo";
git.clone("https://example.com/repo.git", repo);
git.configureUser("me", "me@example.com", "local", repo);
git.add(repo);
git.commit(repo, "my commit");
git.push(repo);
git.pull(repo);
git.createBranch(repo, "feature");
git.checkoutBranch(repo, "feature");
git.status(repo);
```

### Code Interpreter contexts

```java
try (CodeInterpreter ci = CodeInterpreter.create(config)) {
    Context ctx = ci.createCodeContext("/home/user", "python");
    ci.runCode("a = 41", ctx);
    Execution exec = ci.runCode("print(a + 1)", ctx); // 42
    ci.restartCodeContext(ctx);
    ci.removeCodeContext(ctx);
}
```

### Templates

```java
// List / inspect
Template.list(config);
Template.get(templateId, config);

// Build a template from a container image and wait until it's ready
Template.buildFromImage("my-alias", "python:3.12-slim", config, 600);

// Update / publish / delete
Template.setPublic(templateId, true, config);
Template.delete(templateId, config);
```

### Storage & network mounts

Attach OSS / NAS / JuiceFS storage and VPC networking through `StorageMounts` (delivered via sandbox metadata):

```java
import dev.e2b.sdk.storage.StorageMounts;

Map<String, String> metadata = StorageMounts.builder()
        .oss(ossConfig)
        .nas(nasConfig)
        .vpc(vpcConfig)
        .roleArn("acs:ram::...:role/...")
        .build();

Sandbox.create("base", config, NewSandbox.builder().metadata(metadata).build());
```

## Configuration

`ConnectionConfig` controls how the SDK reaches the API and sandboxes:

| Option | Default | Description |
|---|---|---|
| `apiKey` | `E2B_API_KEY` env | API key |
| `domain` | `e2b.app` / `E2B_DOMAIN` env | API & sandbox domain |
| `apiUrl` | `https://api.{domain}` / `E2B_API_URL` env | Override the control-plane URL |
| `requestTimeout` | `60.0` | Request timeout (seconds) |
| `headers` / `apiHeaders` / `extraSandboxHeaders` | — | Extra request headers |
| `httpClient` | shared client | Reuse a single OkHttp client (connection/dispatcher pooling) |
| `debug` | `false` / `E2B_DEBUG` env | Debug logging; uses `http` for sandbox URLs |

## Building & testing

```bash
mvn clean install            # build + unit tests
mvn -Pe2e test               # run end-to-end tests (needs E2B_API_KEY, live gateway)
```

Unit tests run against a mock server and require no credentials. End-to-end tests are grouped under the `e2e` JUnit tag and are excluded from the default build; enable them with the `e2e` profile. See [`docs/SDK_CAPABILITIES_AND_E2E.md`](docs/SDK_CAPABILITIES_AND_E2E.md) for the full capability matrix and E2E coverage.

## Documentation

- Capability & E2E matrix: [`docs/SDK_CAPABILITIES_AND_E2E.md`](docs/SDK_CAPABILITIES_AND_E2E.md)
- E2B docs: [e2b.dev/docs](https://e2b.dev/docs)
