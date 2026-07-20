# E2B Java SDK (阿里云云沙箱)

Java SDK for [阿里云云沙箱 (FC Agent Sandbox)](https://aliyun-fc.github.io/fc-docs/docs/zh-CN/01.%E4%BA%91%E6%B2%99%E7%AE%B1/) — an E2B‑compatible client for running AI‑generated code and commands in secure, isolated cloud sandboxes, from the JVM.

云沙箱原生兼容 E2B。本 SDK 是其 Java 客户端,让你可以在 JVM 上创建并控制沙箱:执行命令、读写文件、运行代码 (Code Interpreter)、构建模板、暂停/恢复等。

> **地域**:云沙箱目前仅支持华北2(北京)。

## Quickstart

### 1. Install

Maven:

```xml
<dependency>
    <groupId>com.alibaba.serverless</groupId>
    <artifactId>e2b-java-sdk</artifactId>
    <version>2.1.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'com.alibaba.serverless:e2b-java-sdk:2.1.0'
```

Requires **Java 8+**.

### 2. Configure credentials & endpoint

Get an API Key from the console (see [创建 API Key](https://aliyun-fc.github.io/fc-docs/docs/zh-CN/01.%E4%BA%91%E6%B2%99%E7%AE%B1/)), then point the SDK at the Beijing endpoint:

```bash
export E2B_API_KEY=e2b_***
export E2B_API_URL=https://api.cn-beijing.e2b.fc.aliyuncs.com
export E2B_DOMAIN=cn-beijing.e2b.fc.aliyuncs.com
```

> The SDK defaults to the public E2B domain (`e2b.app`). To use 阿里云云沙箱 you **must** set `E2B_API_URL` / `E2B_DOMAIN` (or pass `apiUrl` / `domain` to `ConnectionConfig`). Refer to the official docs for the up-to-date regional endpoint.

### 3. Start a sandbox and run commands

```java
import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.model.CommandResult;

ConnectionConfig config = ConnectionConfig.builder()
        .apiKey(System.getenv("E2B_API_KEY"))
        .apiUrl(System.getenv("E2B_API_URL"))
        .domain(System.getenv("E2B_DOMAIN"))
        .build();

// try-with-resources kills the sandbox automatically on close()
try (Sandbox sandbox = Sandbox.create(config)) {
    CommandResult result = sandbox.getCommands().run("echo 'Hello from Sandbox!'");
    System.out.println(result.getStdout()); // Hello from Sandbox!

    sandbox.getFiles().write("/home/user/hello.txt", "Hello, world!");
    String content = sandbox.getFiles().read("/home/user/hello.txt");
    System.out.println(content); // Hello, world!
}
```

### 4. Code execution with the Code Interpreter

Execute Python and get rich results (stdout/stderr, values, errors) back. Uses the `code-interpreter-v1` template by default:

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
Sandbox sandbox = Sandbox.create("code-interpreter-v1", config);

// Create with options
NewSandbox opts = NewSandbox.builder()
        .timeout(600)                          // seconds
        .metadata(Map.of("owner", "team-a"))   // arbitrary key/value metadata
        .envVars(Map.of("MY_VAR", "value"))    // environment variables
        .autoPause(true)                       // pause instead of kill on timeout
        .build();
Sandbox sandbox = Sandbox.create("base", config, opts);

// Connect to a running sandbox / resume a paused one
Sandbox again = Sandbox.connect(sandboxId, config);

sandbox.setTimeout(300);   // extend timeout
sandbox.getInfo();         // id, state (running/paused), template, metadata, ...
sandbox.isRunning();       // liveness check
sandbox.getMetrics();      // CPU / memory / disk metrics
sandbox.getHost(3000);     // public hostname for an exposed port
sandbox.pause();           // preserve state; resume later via connect()
sandbox.kill();            // terminate now

// List sandboxes (defaults to running; supports metadata/state filters + pagination)
Sandbox.list(config);
Sandbox.kill(sandboxId, config);
Sandbox.pause(sandboxId, config);
```

### Commands

```java
Commands cmd = sandbox.getCommands();

cmd.run("ls -la");                                    // foreground, waits for exit
cmd.runOrThrow("test -f /tmp/x");                     // throws on non-zero exit
CommandHandle handle = cmd.runBackground("sleep 30"); // background process
handle.getPid();
handle.waitForExit();

cmd.list();                                           // running processes
cmd.sendStdin(handle.getPid(), "input\n");            // write to stdin
cmd.kill(handle.getPid());                            // terminate a process
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
sandbox.downloadUrl("/home/user/b.txt");              // pre-signed download URL
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

Built-in templates include `base` and `code-interpreter-v1`. You can also build a custom template from a container image:

```java
// List / inspect
Template.list(config);
Template.get(templateId, config);

// Build a custom template from a container image and wait until it's ready
Template.buildFromImage("my-alias", "python:3.12-slim", config, 600);

// Update / publish / delete
Template.setPublic(templateId, true, config);
Template.delete(templateId, config);
```

> Custom template builds are image-based (`fromImage`); each template supports one build.

### Storage & network mounts (FC Extensions)

云沙箱 can attach Alibaba Cloud storage and VPC networking via the `StorageMounts` helper (delivered through sandbox `metadata`):

```java
import dev.e2b.sdk.storage.StorageMounts;

Map<String, String> metadata = StorageMounts.builder()
        .oss(ossConfig)                        // dynamically mount OSS
        .nas(nasConfig)                        // mount NAS
        .vpc(vpcConfig)                        // bind to a VPC
        .roleArn("acs:ram::<uid>:role/<name>") // RAM role (required for OSS)
        .build();

Sandbox.create("base", config, NewSandbox.builder().metadata(metadata).build());
```

## Configuration

`ConnectionConfig` controls how the SDK reaches the API and sandboxes:

| Option | Default | Description |
|---|---|---|
| `apiKey` | `E2B_API_KEY` env | Cloud sandbox API key (required) |
| `domain` | `E2B_DOMAIN` env / `e2b.app` | Sandbox domain — set to the regional domain (e.g. `cn-beijing.e2b.fc.aliyuncs.com`) |
| `apiUrl` | `E2B_API_URL` env / `https://api.{domain}` | Control-plane API endpoint |
| `requestTimeout` | `60.0` | Request timeout (seconds) |
| `headers` / `apiHeaders` / `extraSandboxHeaders` | — | Extra request headers |
| `httpClient` | shared client | Reuse a single OkHttp client (connection/dispatcher pooling) |
| `debug` | `false` / `E2B_DEBUG` env | Debug logging |

## Building & testing

```bash
mvn clean install            # build + unit tests (no credentials needed)
mvn -Pe2e test               # end-to-end tests (needs E2B_API_KEY + regional endpoint)
```

Unit tests run against a mock server. End-to-end tests are grouped under the `e2e` JUnit tag and excluded from the default build. See [`docs/SDK_CAPABILITIES_AND_E2E.md`](docs/SDK_CAPABILITIES_AND_E2E.md) for the capability matrix and E2E coverage.

## Documentation

- 云沙箱官方文档: [aliyun-fc.github.io/fc-docs](https://aliyun-fc.github.io/fc-docs/docs/zh-CN/01.%E4%BA%91%E6%B2%99%E7%AE%B1/)
- Capability & E2E matrix: [`docs/SDK_CAPABILITIES_AND_E2E.md`](docs/SDK_CAPABILITIES_AND_E2E.md)
- E2B compatibility: [E2B docs](https://e2b.dev/docs)
