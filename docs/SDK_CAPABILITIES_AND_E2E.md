# fc-e2b Java SDK — 能力清单与 E2E 覆盖

## 一、SDK 能力清单

### Sandbox（`dev.e2b.sdk.Sandbox`）
| 能力 | 方法 |
|---|---|
| 创建沙箱 | `create(template, config[, NewSandbox])` / `create(NewSandbox, config)` |
| 连接已存在沙箱 | `connect(sandboxId, config[, timeout])` |
| 销毁 | `kill()` / `kill(sandboxId, config)` |
| 暂停 | `pause()` / `pause(sandboxId, config)` |
| 恢复 | `connect(...)`（auto-resume 由 `NewSandbox.autoResume` 控制） |
| 设置超时 | `setTimeout(seconds)` / `setTimeout(sandboxId, seconds, config)` |
| 查询信息 | `getInfo()` / `getInfo(sandboxId, config)` |
| 存活探测 | `isRunning()` |
| 列表 + 过滤 | `list(config[, SandboxQuery, limit, nextToken])`（按 metadata / state 过滤） |
| 指标 | `getMetrics([start, end])` |
| 端口主机名 | `getHost(port)` / `getMcpUrl()` |
| 网络更新 | `updateNetwork(SandboxNetworkUpdate)` |
| 快照 | `createSnapshot(name)` / `listSnapshots([...])` / `deleteSnapshot(id, config)` |
| 文件下载链接 | `downloadUrl(path[, user])` |

### 创建参数（`dev.e2b.sdk.model.NewSandbox`）
`templateId` / `templateName`、`timeout`、`metadata`、`envVars`、`secure`、`allowInternetAccess`、`autoPause`、`autoResume`、`network`、`mcp`、`volumeMounts`

### Commands（`sandbox.getCommands()`）
| 能力 | 方法 |
|---|---|
| 同步执行 | `run(cmd[, envs, user, cwd, timeout, background])` / `run(cmd)` / `runOrThrow(cmd)` |
| 后台执行 | `runBackground(cmd[, envs, user, cwd])` → `CommandHandle`（`getPid` / `isDone` / `waitForExit` / `disconnect`） |
| 进程列表 | `list()` |
| 写入 stdin | `sendStdin(pid, data)` |
| 终止进程 | `kill(pid)` |

### Filesystem（`sandbox.getFiles()`）
| 能力 | 方法 |
|---|---|
| 读 | `read(path[, user])` / `readBytes(path[, user])` |
| 写 | `write(path, content/bytes/stream[, user, metadata])` |
| 列目录 | `list(path[, depth, user])` |
| 存在判断 | `exists(path[, user])` |
| 元信息 | `getInfo(path[, user])` |
| 删除 | `remove(path[, user])` |
| 重命名 | `rename(oldPath, newPath[, user])` |
| 建目录 | `makeDir(path[, user])` |
| 下载链接 | `downloadUrl(path[, user])` |

### Git（`sandbox.getGit()`）
`clone`、`init`、`status`、`branches`、`add`、`commit`、`push`、`pull`、`createBranch`、`checkoutBranch`、`deleteBranch`、`reset`、`remoteAdd`、`setConfig`、`configureUser`

### Template（`dev.e2b.sdk.Template`）
| 能力 | 方法 |
|---|---|
| 列表 | `list(config)` |
| 详情 | `get(templateId, config[, limit, nextToken])` |
| 创建（v3） | `createV3(TemplateBuildRequestV3, config)` |
| 镜像构建并等待就绪 | `buildFromImage(alias, image, config, timeoutSeconds)` |
| 触发构建 | `startBuild(templateId, buildId, TemplateBuildStartV2, config)` |
| 构建状态 / 日志 | `getBuildStatus(...)` / `getBuildLogs(...)` |
| 更新 / 设为公开 | `update(...)` / `setPublic(templateId, isPublic, config)` |
| 删除 | `delete(templateId, config)` |
| 创建（v2，已废弃） | `create(TemplateBuildRequestV2, config)` `@Deprecated` → 用 `createV3` / `buildFromImage` |

### Code Interpreter（`dev.e2b.sdk.codeinterpreter.CodeInterpreter`）
| 能力 | 方法 |
|---|---|
| 创建 / 包装沙箱 | `create([template,] config[, NewSandbox])` / `from(sandbox)` |
| 执行代码 | `runCode(code[, language])` / `runCode(code, Context)` / `runCode(code, language, contextId, envVars, ...)` → `Execution`（results / logs / error / executionCount） |
| 上下文管理 | `createCodeContext([cwd, language])` / `listCodeContexts()` / `removeCodeContext(...)` / `restartCodeContext(...)` |

### 存储 / 网络挂载（`dev.e2b.sdk.storage.StorageMounts`）
通过 metadata 下发，Builder 支持：`vpc(VpcConfig)`、`juicefs(JuiceFsConfig)`、`oss(OssConfig)`、`nas(NasConfig)`、`roleArn(...)`

---

## 二、E2E 覆盖

> 运行：`mvn -Pe2e test`（需 `E2B_API_KEY` 等环境变量）。下表「状态」中 `gated` 表示需额外环境变量/资源才会执行。

| 测试类 | 覆盖能力 | 状态 |
|---|---|---|
| `BasicSandboxE2eTest` | create / run / kill | ✅ |
| `CommandsE2eTest` | commands run（envs/user/cwd/timeout） | ✅ |
| `ProcessManagementE2eTest` | runBackground / list / sendStdin / kill | ✅ |
| `FilesystemE2eTest` | read/write/list/rename/makeDir/二进制 | ✅ |
| `GitE2eTest` | git clone | ✅ |
| `GitExtendedE2eTest` | git add/commit/branch/config | ✅ |
| `EnvVarsE2eTest` | create-time envVars（可选 `E2E_BASE_TEMPLATE_IMAGE` 先建临时模板，对齐 py-06） | ✅ |
| `DynamicPortE2eTest` | getHost / 动态端口 | ✅ |
| `MetadataE2eTest` | metadata + Sandbox.list 过滤 | ✅ |
| `CodeInterpreterE2eTest` | runCode / 富结果 / envVars / 上下文 / 错误捕获 | ✅ |
| `TemplateE2eTest` | Template list / get | ✅ |
| `TemplateBuildE2eTest` | buildFromImage（v3）→ 建沙箱运行 | gated（`E2E_BUILD_IMAGE`） |
| `PauseResumeE2eTest` | pause + connect 恢复并保留文件 | ✅ |
| `StaticPauseE2eTest` | pause | ✅ |
| `SandboxLifecycleE2eTest` | setTimeout / getInfo / isRunning / list | ✅ |
| `LifecycleE2eTest` | autoPause / autoResume / setTimeout | ✅ |
| `InternetAccessE2eTest` | allowInternetAccess（true / false） | ✅（egress 强制依赖环境） |
| `SnapshotAndNetworkE2eTest` | createSnapshot / updateNetwork | ✅（运行时 egress 依赖环境） |
| `OssMountE2eTest` | OSS 挂载读写 | ✅ |
| `VpcNasE2eTest` | VPC 绑定 + NAS 挂载 | ✅ |
| `JuiceFsE2eTest` | JuiceFS 挂载读写 | gated（`E2E_JUICEFS_*`） |

### 单元测试
`SandboxTest`、`TemplateTest`、`CommandsStreamTest`、`CodeInterpreterParseTest`（NDJSON 解析）、`StorageMountsTest`（挂载 metadata 序列化）— 共 25 项。
