# 构建与运行（build-and-run）

> 本文覆盖：环境要求、版本矩阵、依赖、常用 Gradle 任务、运行目录、CI、常见故障。
> 关联文档：[issues/open.md](issues/open.md)（仍未解决的构建/运行问题）、[conventions.md](conventions.md)。

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
// 版本来自 gradle.properties 的 particledrawing_version（现为 1.0.11-ALPHA）
compileOnly  files("lib/particledrawing-${particledrawing_version}.jar")   // 编译期接口
localRuntime files("lib/particledrawing-${particledrawing_version}.jar")   // 开发环境运行时（不传递给下游）
```

- `particledrawing`（ParticleDrawing）是**必需的模组前置**：`neoforge.mods.toml` 声明 `type="required"`、`versionRange="${particledrawing_version_range}"`（现为 `[1.0.11-ALPHA,)`）、`ordering="AFTER"`。
- jar 以**本地文件**形式签入 `lib/`，不走 Maven；文件名与版本区间都由 `gradle.properties` 驱动（`particledrawing_version` / `particledrawing_version_range`），`build.gradle` 与 `mods.toml` 不再硬编码。
- 代码中用到的 API 位于 `work.nekow.particledrawing.api.*`：`ParticleManager`（`create`/`trackAll`/`setVelocityAll`/`applyForceAll`）、
  `ParticleHandle`（`setVelocity`/`track`/`position`/`applyForce`/`attachTo`/`attachToLocal`/`remove`）、`Color`；
  另有 `ParticleBatch`（**程序化粒子集的推荐入口**：`add`/`trackAll`/`setVelocityAll`/`applyForceAll`/`forEach`/`removeIf`/`ensureSize`/`evictDead`）、`ParticleGroup`（编排式动画）、`EffectCallbacks`、`EffectVarStore`。
- ✅ **构建口径（同名换 jar 的坑，已解决）**：2026-09-30 当天曾在**同一版本号字符串**下替换过 `lib/` 里的 jar，
  旧构建缺 `position`/`applyForce`/`attachTo`/`trackAll` 而 `versionRange` 拦不住。
  现在 PD 每加一批 API 就提版本（当前 **1.0.11-ALPHA**），本模组的下限同步为 `[1.0.11-ALPHA,)`，
  旧构建在加载期就会被拒绝，**不需要再靠日期约定**。
- ⚠️ **预发布版本的版本区间（踩过一次的坑）**：FML 用 Maven 版本语义比较，`1.0.11-ALPHA` 排序**低于** `1.0.11`，
  所以下限写 `[1.0.11,)` 会报 `Mod primalspells requires particledrawing 1.0.11 or above / Currently, particledrawing is 1.0.11-ALPHA`。
  下限必须带上预发布标识：`[1.0.11-ALPHA,)`（规则在 1.0.8-ALPHA 那次实测过：带标识的下限接受该预发布版与之后的版本，拒绝对应更低的版本；比较大小写不敏感）。
- **升级依赖的三步**（都只改 `gradle.properties` + 放 jar）：
  1. 新 jar（含 `-sources.jar` 更好）放进 `lib/`；
  2. 改 `gradle.properties` 的 `particledrawing_version`（= jar 文件名里的版本段）与 `particledrawing_version_range`（下限 = 该版本，预发布要带标识）；
  3. `./gradlew build --no-build-cache`。
- ⚠️ **若第 3 步失败**：不要在本模组绕过或自研替代实现 —— 按
  [conventions.md](conventions.md) 第 9 节的政策停下，并把 API 修改需求登记到
  [issues/pd-api-requests.md](issues/pd-api-requests.md)（政策全文见 [design/effects-api.md](design/effects-api.md) 第 0 节）。
- 当前锁定版本为 **1.0.12-ALPHA**（一次实现四个历史需求：增量追加按「从现在起」读、旋转可叠加、
  `scale`/`pulse` 连到轴心的距离一起缩放、`move` 不再改 arm 基准轴心 —— 详见
  [issues/resolved.md](issues/resolved.md) 附录 B 与第 20 轮）。升级时**务必重审本侧「自己补偿时钟」的写法**：
  第 17~19 轮为绕开旧语义自己算过游标，新版语义下会重复计时（第 20 轮踩过）。
- 上一版本为 **1.0.11-ALPHA**（含 `ParticleBatch` 与实体锚点三入口、1.0.10 的批量设速/批量施力、1.0.11 的「组轴心语义」文档化与指令字段清理；近几个版本都只新增 API 或补文档，无破坏性变更）；
  `docs/design/effects-api.md` 中「依赖升级 / `Trajectory` 迁移」已完成，`.pdrawc` 动画特效接线仍未落地。
- `lib/` 只保留当前版本的 jar（`particledrawing-1.0.11-ALPHA.jar` + `-sources.jar`）；旧版本已清理，**对照 API 请以当前版本的 `-sources.jar` 为准**。

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
./gradlew runCapture          # 实机录制：真客户端自动跑场景并抓帧（见 §9）
python tools/capture/assemble.py   # 把帧合成 mp4/gif/拼图并判定
```

`build.gradle` 中自定义的运行配置（`runs { … }`）：

- `client`：窗口 1708×960，随机用户名 `Player####`（便于多开调试）。
- `capture`：**实机录制**（`runType 'client'`，工作目录 `run/capture`，1280×720，场景由 `-Pcapture=` 选，见 §9）。
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
| 启动后立刻崩溃且日志提到 `Missing element ResourceKey[minecraft:damage_type / primalspells:projectile]` | 历史上出现过（见 [issues/resolved.md](issues/resolved.md) 附录 A）：伤害类型数据包未加载时投射物结算会抛错，注意存档/`run` 目录的数据包状态。 |
| 改了包名/移动文件后编译报「unresolved reference」 | 目录移动后 `package` 与 `import` 必须同步；用 [conventions.md](conventions.md) 第 8 节的对照表核对，并跑 `./gradlew compileKotlin`。 |
| Parchment 参数名缺失 | MC 26.2 尚无 Pascal 版本，`gradle.properties` 里相关行保持注释状态（见其中说明）。 |
| 想看中文/英文界面文本 | 语言键在 `src/main/resources/assets/primalspells/lang/{zh_cn,en_us}.json`。 |

## 7. 离线数值自检（不启动游戏，直接跑实现）

纯几何/数值逻辑（选点、拓扑、轨迹递推）可以**不启动游戏**直接执行真实实现：用 Gradle 缓存里的
Kotlin 编译器手工编译「目标文件 + 一个同语义的最小替身」，再用 `java` 跑一个 `main` 打印断言结果。
第 15 轮的截角二十面体几何就是这么验的（`util/Polyhedra.kt`：32 面板 / 60 顶点 / 90 棱 / 棱长一致 / 共面 / 邻接分类）。

要点（脚本与输出都在 `build/verify/`，**`build/` 不入库**，需要时按下面五步重建）：

1. 从 `~/.gradle/caches/modules-2/files-2.1/` 找齐这几个 jar（缺哪个补哪个，版本取本机最新的 2.x）：
   `org.jetbrains.kotlin:kotlin-compiler-embeddable`、`kotlin-stdlib`、`kotlin-reflect`、`kotlin-script-runtime`、
   `org.jetbrains:annotations`、`org.jetbrains.intellij.deps:trove4j`、`org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm`
   （把 jar 拷到一个目录里，用 `java -cp "libs/*"` 加载，避免 Git Bash 下 Windows 类路径分隔符被转换）。
2. 写一个 `net.minecraft.world.phys.Vec3` 替身（只实现目标文件用到的方法，语义与 MC 一致）。
3. 写 `Main.kt`：调用真实实现，把不变量写成断言，失败项计数后以非零退出。
4. 编译：`java -cp "libs/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "libs/kotlin-stdlib-<版本>.jar" -d out <目标.kt> Vec3Stub.kt Main.kt`
5. 运行：`java -Dstdout.encoding=UTF-8 -cp "out;libs/kotlin-stdlib-<版本>.jar" harness.MainKt`
   （不加 `-Dstdout.encoding` 时重定向到文件会按 GBK 写出，中文断言会变乱码）。

## 8. 建议的工作流

1. 改代码 → `./gradlew compileKotlin`（快速反馈）。
2. 功能可用性 → `./gradlew runClient`，进开发世界实测；崩溃看 `run/client/crash-reports/`，告警看 `run/client/logs/latest.log`。
   （**没人操作电脑时**用 §9 的 `pwsh tools/capture/capture.ps1` —— 它会把实机画面录成视频给你看。）
3. 交付前 → `./gradlew build`，并确认资源三件套（items/models/textures/lang）齐全（对照表见 [spell-development.md](spell-development.md)）。

---

## 9. 实机录制：一条命令看到「游戏里实际长什么样」

第 7 节的离线自检只能验数值/几何；「粒子在屏幕上长什么样、动画有没有按设计跑」必须进游戏看。
这一节把「开客户端 → 进世界 → 摆位置施法 → 按 F2 截图」整条链自动化：**真客户端 + 集成服务端跑真实施法路径**
（`Wand.spell` / `Wand.tick`），逐 tick 抓一帧，合成视频并给出 PASS/FAIL 判定。

```powershell
pwsh tools/capture/capture.ps1                      # 三个场景全录（约 1 分钟）
pwsh tools/capture/capture.ps1 -Scene black_hole    # 只录一个（逗号分隔可多个）
pwsh tools/capture/capture.ps1 -SkipBuild           # 跳过前置的 gradlew build
pwsh tools/capture/capture.ps1 -NoVideo             # 只判定 + 拼图，不调 ffmpeg

# 等价的手工两步：
./gradlew runCapture -Pcapture=all        # 录制（驱动录完自己退出）
python tools/capture/assemble.py          # 合成 + 判定
```

产物都在 `build/capture/out/`（`build/` 不入库）：

| 路径 | 内容 |
|---|---|
| `index.html` | **画廊**：三个场景并排（视频 + 动图 + 拼图 + 判定）—— 只想「看一眼」就看这个 |
| `report.txt` | 判定结果（PASS/FAIL + 每个失败项） |
| `<scene>/<scene>.mp4` | H.264 / 20fps / 1280×720 |
| `<scene>/<scene>.gif` | 640 宽动图（贴到聊天里直接能动） |
| `<scene>/contact.png` | 12 帧拼图（一眼看完整条时间线） |
| `<scene>/frames/*.png` | 逐 tick 原始帧 + `frames.tsv`（帧号 ↔ tick ↔ gameTime） |

### 9.1 驱动做了什么（`client/capture/CaptureDriver.kt`）

| 阶段 | 行为 |
|---|---|
| 启动 | 关失焦暂停、隐藏 HUD、静音、关垂直同步、粒子设 ALL、渲染距离 8 |
| 建世界 | 存档不存在就现场建**固定种子的超平坦世界**（`WorldOpenFlows.createFreshLevel`），存档存在则直接打开 |
| 布景 | `/gamerule advance_time false` 等命令冻结时间/天气/刷怪，玩家进旁观（护盾场景用创造） |
| 机位 | 每个场景一条**相机轨迹**（绕球心公转 / 第三人称转台），由服务端权威地每 tick 传送玩家 |
| 施法 | 走真实路径：造 `Wand` → `wand.spell(player)`；被动（护盾）走 `wand.tick(caster, level)` |
| 抓帧 | 客户端 tick 时记「本 tick 还没抓」，渲染线程在**本 tick 的第一帧**抓一次 → 恰好 20fps、每 tick 一帧 |
| 断言 | 场景内检查（施法成功 / 清理干净 / 被动装载）写进 `meta.tsv`，由装配脚本收口 |
| 收尾 | 等尾部帧落盘 → `mc.stop()` 自己退出（无人值守） |

抓帧口径值得强调：**渲染帧率远高于 20Hz**，逐帧全录只会得到大量重复帧；
「每 tick 一帧」既与游戏的实际动画步进一致，又让 100 多帧的体积可控。

### 9.2 场景与判定口径

| 场景 | 帧数 | 看什么 |
|---|---|---|
| `black_hole` | 108 | 整壳从中心长出（14t）→ 稳态 → t=70 触发正常死亡 → **8t 加速砸进中心 → 反弹小球（10t）→ 收成一点**（三拍各自带镜头冲击） |
| `gravity_field` | 130 | 球形范围 + 下坠粒子 + 第 13t 开场重砸 + 落地溅射 + 每 22t 一层波环 + 地面井环 |
| `energy_shield` | 150 | 固定球体逐块搭起（16t）→ **合拢一记冲击波** → t=100「放下法杖」→ 逐块淡出消散 |

`assemble.py` 的判定（**非零退出 = 有失败项**，可直接当 CI 门）：

1. **帧数** = 场景声明长度，且驱动侧 `framesDropped == 0`；
2. **场景内断言**全部通过（施法/清理/被动装载/**结束时维度内剩余 PD 粒子数 = 0**）；
3. **空场起飞**：开头 2 帧几乎没有任何特效像素；
4. **真的渲染了**：峰值特效像素超过阈值（证明粒子确实上了屏幕，而不是只有服务端状态对）；
5. **收干净了**：最后 3 帧几乎没有任何特效像素；
6. **退场是渐变的**（设计上有退场动画的效果：黑洞/护盾）或**立即且干净**（设计上没有的：重力场）。

特效像素的判据是「偏蓝的亮像素」——黑底摄影棚里只有粒子满足（木桩、地面、天空都不满足），
所以这条曲线同时充当「特效出现了/消失了/逐渐消失了」的证据。

### 9.3 注意事项

- 驱动只在**开发环境 + 系统属性**下注册（`CaptureDriver.shouldInstall()`）；
  生产 jar 里 `CaptureDriver` 整个类不会被加载，也不注册任何监听器。
- 每次跑都会删掉 `run/capture` 并从固定种子重建世界 —— **不碰 `run/client` 的存档与配置**。
- 画面里的**黑底「摄影棚」**（地面与四壁换成黑色混凝土）是为了让细线粒子有对比度，属于**取景环境**，
  不是玩法场景。想录自然地形：删掉 `CaptureScenes` 里的 `ctx.buildStudio(...)` 调用即可。
- 加场景 = 两处一起改：`client/capture/CaptureScenes.kt` 的 `all()` 与 `tools/capture/assemble.py` 的 `SCENES` 表
  （帧数/退场口径要对齐，否则判定会拦下来）。
- 依赖：`ffmpeg`（PATH 里）+ `python`（`numpy` / `pillow`）；缺 ffmpeg 时 `-NoVideo` 仍能做判定与拼图。
- 录制是**真机渲染**（AMD RX 7800 XT / 1280×720），窗口会真的弹出来；跑的时候别去动键盘鼠标。
