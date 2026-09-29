# 构建与运行（build-and-run）

> 本文覆盖：环境要求、版本矩阵、依赖、常用 Gradle 任务、运行目录、CI、常见故障。
> 关联文档：[known-issues.md](known-issues.md)（当前已知的构建/运行问题）、[conventions.md](conventions.md)。

---

## 1. 版本矩阵

| 项 | 值 | 出处 |
|---|---|---|
| Minecraft | 26.2 | `gradle.properties` → `minecraft_version` |
| NeoForge | 26.2.0.59 | `gradle.properties` → `neo_version` |
| Java | 25（toolchain） | `build.gradle` → `java.toolchain.languageVersion` |
| Kotlin | 2.4.10 | `gradle.properties` → `kotlin_version` |
| Gradle | 9.6.1（wrapper） | `gradle/wrapper/gradle-wrapper.properties` |
| 模组版本 | 1.0.0（`mod_version`） | `gradle.properties` |
| modId | `primalspells` | `gradle.properties` / `@Mod(PrimalSpells.MODID)` |
| 许可证 | GNU AGPLv3 | `gradle.properties` → `mod_license` |

JDK 由 `foojay-resolver-convention` 插件按 toolchain 自动下载；本机只需能运行 Gradle 的 JVM，不必手动装 JDK 25。

## 2. 依赖

```groovy
// 版本来自 gradle.properties 的 particledrawing_version（现为 1.0.8-ALPHA）
compileOnly  files("lib/particledrawing-${particledrawing_version}.jar")   // 编译期接口
localRuntime files("lib/particledrawing-${particledrawing_version}.jar")   // 开发环境运行时（不传递给下游）
```

- `particledrawing`（ParticleDrawing）是**必需的模组前置**：`neoforge.mods.toml` 声明 `type="required"`、`versionRange="${particledrawing_version_range}"`（现为 `[1.0.8-ALPHA,)`）、`ordering="AFTER"`。
- jar 以**本地文件**形式签入 `lib/`，不走 Maven；文件名与版本区间都由 `gradle.properties` 驱动（`particledrawing_version` / `particledrawing_version_range`），`build.gradle` 与 `mods.toml` 不再硬编码。
- 代码中用到的 API 位于 `work.nekow.particledrawing.api.*`：现用 `ParticleManager`、`ParticleHandle`
  （`setVelocity`/`track`/`remove`/`velocity`/`data`）、`Color`；1.0.8-ALPHA 另有 `EffectCallbacks`、`EffectVarStore`（动画特效接线时用）。
- ⚠️ **预发布版本的版本区间（踩过一次的坑）**：FML 用 Maven 版本语义比较，`1.0.8-ALPHA` 排序**低于** `1.0.8`，
  所以下限写 `[1.0.8,)` 会报 `Mod primalspells requires particledrawing 1.0.8 or above / Currently, particledrawing is 1.0.8-ALPHA`。
  下限必须带上预发布标识：`[1.0.8-ALPHA,)`（实测接受 `1.0.8-ALPHA`/`1.0.8`/`1.0.8-beta`/`1.0.9-ALPHA`，拒绝 `1.0.7`；比较大小写不敏感）。
- **升级依赖的三步**（都只改 `gradle.properties` + 放 jar）：
  1. 新 jar（含 `-sources.jar` 更好）放进 `lib/`；
  2. 改 `gradle.properties` 的 `particledrawing_version`（= jar 文件名里的版本段）与 `particledrawing_version_range`（下限 = 该版本，预发布要带标识）；
  3. `./gradlew build --no-build-cache`。
- ⚠️ **若第 3 步失败**：不要在本模组绕过或自研替代实现 —— 按
  [conventions.md](conventions.md) 第 9 节的政策停下，并把 API 修改需求登记到
  [known-issues.md](known-issues.md#7-particledrawing-api-需求待对方实现)（政策全文见 [design/effects-api.md](design/effects-api.md) 第 0 节）。
- 当前锁定版本为 **1.0.8-ALPHA**（1.0.8 相对 1.0.6 只新增 API，无破坏性变更）；
  `docs/design/effects-api.md` 中「依赖升级 / `Trajectory` 迁移」已完成，`.pdrawc` 动画特效接线仍未落地。
- `lib/` 里可能残留不再被引用的旧版本 jar（如 `particledrawing-1.0.6.jar`）：**对照 API 请以当前版本的 `-sources.jar` 为准**。

## 3. 常用命令

在仓库根目录执行（Windows 用 `gradlew.bat`，Linux/macOS 用 `./gradlew`）：

```bash
./gradlew build              # 编译 + 打包（交付前必跑；CI 也跑这条）
./gradlew compileKotlin      # 只做编译，最快的语法/包名检查
./gradlew runClient          # 启动开发客户端（读取 run/client）
./gradlew runServer          # 启动开发服务端（--nogui，读取 run/server）
./gradlew gameTestServer     # 跑 GameTest 后退出
./gradlew runData            # 数据生成（clientData 配置，输出到 src/generated/resources/）
./gradlew --offline build     # 依赖已缓存时可离线构建（首次不可用）
```

`build.gradle` 中自定义的运行配置（`runs { … }`）：

- `client`：窗口 1708×960，随机用户名 `Player####`（便于多开调试）。
- `server`：`--nogui`。
- 三者都设置了 `neoforge.enabledGameTestNamespaces = primalspells`。
- 工作目录：`run/<配置名>`（`client` → `run/client`）。

## 4. 运行目录与产物

| 路径 | 内容 | 是否入库 |
|---|---|---|
| `build/` | Gradle 产物、NeoForm 反编译缓存（体积很大） | 否（`.gitignore`） |
| `run/client/` | 开发客户端工作目录：存档、日志、崩溃报告、配置 | 否 |
| `run/client/logs/latest.log` | 最近一次运行日志 | 否 |
| `run/client/crash-reports/` | 崩溃报告（排障第一现场） | 否 |
| `src/generated/resources/` | 数据生成输出（已加入资源源集） | 是（按需） |
| `lib/` | 本地前置 jar（particledrawing） | 是 |
| `repo/` | `maven-publish` 的本地发布目录 | 否 |

## 5. CI

`.github/workflows/build.yml`：push / PR 时在 `ubuntu-latest` 上安装 **Temurin JDK 25**，用 `gradle/actions/setup-gradle` 缓存，执行 `chmod +x ./gradlew` 后跑 `./gradlew build`。没有测试任务（项目当前无单元测试）。

## 6. 常见故障

| 现象 | 原因 / 处理 |
|---|---|
| 首次构建卡在下载 NeoForge / NeoForm | 需要联网访问 `maven.neoforged.net` 与 Mojang 源；成功后缓存在 `build/neoform/`，之后可 `--offline`。 |
| `Unsupported class file major version` / toolchain 报错 | 本机 JVM 太旧；Gradle 自身需能启动的 JVM，项目 toolchain 会另拉 JDK 25。 |
| 运行时报 `Mod file particledrawing not found` | `lib/particledrawing-<particledrawing_version>.jar` 缺失，或 `gradle.properties` 的 `particledrawing_version` 与 `lib/` 里的文件名不一致（见 §2 的升级四步）。 |
| 启动后立刻崩溃且日志提到 `Missing element ResourceKey[minecraft:damage_type / primalspells:projectile]` | 历史上出现过（见 [known-issues.md](known-issues.md)）：伤害类型数据包未加载时投射物结算会抛错，注意存档/`run` 目录的数据包状态。 |
| 改了包名/移动文件后编译报「unresolved reference」 | 目录移动后 `package` 与 `import` 必须同步；用 [conventions.md](conventions.md) 第 8 节的对照表核对，并跑 `./gradlew compileKotlin`。 |
| Parchment 参数名缺失 | MC 26.2 尚无 Pascal 版本，`gradle.properties` 里相关行保持注释状态（见其中说明）。 |
| 想看中文/英文界面文本 | 语言键在 `src/main/resources/assets/primalspells/lang/{zh_cn,en_us}.json`。 |

## 7. 建议的工作流

1. 改代码 → `./gradlew compileKotlin`（快速反馈）。
2. 功能可用性 → `./gradlew runClient`，进开发世界实测；崩溃看 `run/client/crash-reports/`，告警看 `run/client/logs/latest.log`。
3. 交付前 → `./gradlew build`，并确认资源三件套（items/models/textures/lang）齐全（对照表见 [spell-development.md](spell-development.md)）。
