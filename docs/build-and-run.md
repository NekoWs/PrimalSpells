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
// 版本来自 gradle.properties 的 particledrawing_version（现为 1.0.15-ALPHA）
compileOnly  files("lib/particledrawing-${particledrawing_version}.jar")   // 编译期接口
localRuntime files("lib/particledrawing-${particledrawing_version}.jar")   // 开发环境运行时（不传递给下游）
```

- `particledrawing`（ParticleDrawing）是**必需的模组前置**：`neoforge.mods.toml` 声明 `type="required"`、`versionRange="${particledrawing_version_range}"`（现为 `[1.0.15-ALPHA,)`）、`ordering="AFTER"`。
- jar 以**本地文件**形式签入 `lib/`，不走 Maven；文件名与版本区间都由 `gradle.properties` 驱动（`particledrawing_version` / `particledrawing_version_range`），`build.gradle` 与 `mods.toml` 不再硬编码。
- 代码中用到的 API 位于 `work.nekow.particledrawing.api.*`：`ParticleManager`（`create`/`trackAll`/`setVelocityAll`/`applyForceAll`）、
  `ParticleHandle`（`setVelocity`/`track`/`position`/`applyForce`/`attachTo`/`attachToLocal`/`remove`）、`Color`；
  另有 `ParticleBatch`（**程序化粒子集的推荐入口**：`add`/`trackAll`/`setVelocityAll`/`applyForceAll`/`forEach`/`removeIf`/`ensureSize`/`evictDead`）、`ParticleGroup`（编排式动画）、`EffectCallbacks`、`EffectVarStore`。
- ✅ **构建口径（同名换 jar 的坑，已解决）**：2026-09-30 当天曾在**同一版本号字符串**下替换过 `lib/` 里的 jar，
  旧构建缺 `position`/`applyForce`/`attachTo`/`trackAll` 而 `versionRange` 拦不住。
  现在 PD 每加一批 API 就提版本（当前 **1.0.15-ALPHA**），本模组的下限同步为 `[1.0.15-ALPHA,)`，
  旧构建在加载期就会被拒绝，**不需要再靠日期约定**。
- ⚠️ **预发布版本的版本区间（踩过一次的坑）**：FML 用 Maven 版本语义比较，`1.0.11-ALPHA` 排序**低于** `1.0.11`，
  所以下限写 `[1.0.11,)` 会报 `Mod primalspells requires particledrawing 1.0.11 or above / Currently, particledrawing is 1.0.11-ALPHA`。
  下限必须带上预发布标识（当前即 `[1.0.15-ALPHA,)`；规则在 1.0.8-ALPHA 那次实测过：带标识的下限接受该预发布版与之后的版本，拒绝对应更低的版本；比较大小写不敏感）。
- **升级依赖的三步**（都只改 `gradle.properties` + 放 jar）：
  1. 新 jar（含 `-sources.jar` 更好）放进 `lib/`；
  2. 改 `gradle.properties` 的 `particledrawing_version`（= jar 文件名里的版本段）与 `particledrawing_version_range`（下限 = 该版本，预发布要带标识）；
  3. `./gradlew build --no-build-cache`。
- ⚠️ **若第 3 步失败**：不要在本模组绕过或自研替代实现 —— 按
  [conventions.md](conventions.md) 第 9 节的政策停下，并把 API 修改需求登记到
  [issues/pd-api-requests.md](issues/pd-api-requests.md)（政策全文见 [design/effects-api.md](design/effects-api.md) 第 0 节）。
- 当前锁定版本为 **1.0.15-ALPHA**：新增当前倍率接续的scaleTo/scaleBy、零尺寸不绘制、移动组锚点相邻样本插值、空间变量缓动、发射器jitter及寿命/曲线热更新；大批次运动/销毁载荷由PD自动拆包。
- 本地前置源码项目位于 `D:/Backup/IDEA Projects/ParticleDrawing`。本轮执行 `gradlew.bat build sourcesJar --console=plain`，复制build/libs中的主JAR与sources JAR；另外执行 `gradlew.bat test --rerun-tasks --console=plain`，测试成功。
- 增量动画仍按“从现在起”追加，不能自行再补播放游标。发射器`.sizeCurve`是**追加乘数曲线**；重复热更新应使用`.lifeCurve(ParticleLifeCurve.of(ParticleCurve.scale(...)))`覆盖，避免累积到8条通道上限。
- 上一版本为 **1.0.11-ALPHA**（含 `ParticleBatch` 与实体锚点三入口、1.0.10 的批量设速/批量施力、1.0.11 的「组轴心语义」文档化与指令字段清理；近几个版本都只新增 API 或补文档，无破坏性变更）；
  `docs/design/effects-api.md` 中「依赖升级 / `Trajectory` 迁移」已完成，`.pdrawc` 动画特效接线仍未落地。
- `lib/` 只保留当前版本的 jar（`particledrawing-1.0.15-ALPHA.jar` + `-sources.jar`）；旧版本已清理，**对照 API 请以当前版本的 `-sources.jar` 为准**。
  源码 jar **不接进构建**（只作对照用）：Gradle 的 file 依赖没有 sources 变体，2026-10-04 试过
  `compileOnly` + `idea.module.scopes` 想让 IDEA 悬停看到 KDoc，**实测没生效**，已撤回 —— 别再试这条。

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

**只想手工看效果？** 游戏里跑 `/primalspells shielddummy [卡片数]` 会在视线方向 3 格放一个**持盾靶子**
（法杖 + N 张护盾卡，靶子自己驱动被动），拿手上的法术打它就能现场看护盾的受击玻璃；大威力法术（大爆）在
法力不够时会打穿，受击那一圈碎裂并留下 3 秒粉色破口。
想看**第一视角**（自己的护盾被持续攻击）就用 `/primalspells shielddummy attack [法术] [间隔tick]`，
它每 N tick 朝 32 格内最近的玩家打一发（默认火球 / 20 tick，可指定 `laser` 等）。
清理用 `/primalspells shielddummy clear`。
**想直接拿到某一级法杖**（六级曲线见 [design/wand-tiers.md](design/wand-tiers.md)）用
`/primalspells wand t5`（可加 `charged` 拿蓄能型，`debug` 拿调试杖）——它会把这一根掷出的数值回报出来。
实现见 `event/ShieldDummyCommand.kt`、`event/WandCommand.kt` 与 `TestDummyEntity.tick`。

**录制不会打扰你**：客户端窗口默认被隐藏（`CaptureDriver.hideWindow` 走 `GLFW.glfwHideWindow` 并关掉
`FOCUS_ON_SHOW`），并且每 tick 兜底释放鼠标抓取 —— 不会弹窗、抢焦点、锁鼠标。
隐藏窗口在部分 Windows 驱动上会让 framebuffer 回读为 0×0，再被 Window 兜底为 1×1。
驱动在隐藏后显式设置 1280×720 的离屏尺寸；截图读 `gameRenderer.mainRenderTarget()`，装配器对所有帧检查尺寸；
判定里的「特效确实渲染」这条断言就是它的兜底验证。
想看着录制跑（调机位/调场景时有用）：`pwsh tools/capture/capture.ps1` 之外加 `-PcaptureShowWindow=true`
（即 `./gradlew runCapture -PcaptureShowWindow=true`）。

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
| `black_hole` | 108 | 暗核与双层倾斜吸积盘分层淡入（6–14t）→ 稳态旋转/吸入 → t=70 正常死亡 → 原组旋转塌缩并淡出（18t） |
| `black_hole_moving` | 108 | 固定机位：右移 → 急停回圆 → 反向左移 → 停住 → 退场；检查最终定位阶段之后读取场心 |
| `gravity_field_moving` | 130 | 同一移动时间线，检查球壳和地面井环跟随，已发射波面留在来路 |
| `gravity_field` | 130 | 球面经线 + 加速落体细线 + 刻度波面（首层13t落地、后续26t，每13t一层）+ 地面双井环；固定结构10t淡出 |
| `energy_shield` | 150 | 固定球体逐块搭起（16t）→ **合拢一记冲击波** → t=100「放下法杖」→ 逐块淡出消散 |
| `fireball_trail` | 72 | 法术拖尾（第 43 轮新增，第 45 轮定稿）：前置回望机位跟拍火球 —— **白热核 + 沿轨迹收细的符文余痕**（自定义贴图 `rune_spark`）+ 贴身细碎符文；t=33 主动收球 → 之后看余痕逐颗消散 |

`assemble.py` 的判定（**非零退出 = 有失败项**，可直接当 CI 门）：

1. **分辨率与帧数**：所有帧必须为 1280×720；`帧数 + 丢帧数 = 场景声明长度`（丢帧由驱动如实计数），且丢帧率 ≤5%；
2. **场景内断言**全部通过（施法/清理/被动装载/**结束时维度内剩余 PD 粒子数 = 0**）；
3. **空场起飞**：开头 2 帧几乎没有任何特效像素；
4. **真的渲染了**：峰值特效像素超过阈值（证明粒子确实上了屏幕，而不是只有服务端状态对）；
5. **收干净了**：最后 3 帧几乎没有任何特效像素；
6. **退场是渐变的**（设计上有退场动画的效果：黑洞）或**立即且干净**（设计上没有退场动画的场景），
   或者**声明交给专属判据**（`decay: none`）——当一个场景混了两套互不相干的视觉系统
   （护盾 = 受击玻璃板 + 大爆原版烟，全局峰值会逐次运行翻转），全局退场判定就不可靠了；
   这时 spec 必须另有对应断言（护盾用的是粉色 `breach_fade`），脚本会校验它存在；
7. **黑洞移动**：固定机位下，明亮内盘在往返两次停点均须离开出生点超过 60px（用 frames.tsv 定位 tick，避免丢帧偏移）；
8. **护盾破口**（第 26 轮）：破口窗口里应有**常驻**的粉色轮廓，且到期是**淡出**而不是一帧消失。

特效像素的判据是「偏蓝的亮像素」——黑底摄影棚里只有粒子满足（木桩、地面、天空都不满足），
所以这条曲线同时充当「特效出现了/消失了/逐渐消失了」的证据。
护盾的破口轮廓是**粉色**（蓝判据看不见），所以第 26 轮另加了一条粉色判据：**红远高于绿、且绿≈蓝**。
爆炸的内爆/爆发是**暖色**（橙红 / 亮黄），蓝判据同样看不见，所以第 28 轮又加了两条：
- **暖色判据**（`r > 150` 且 `r − b > 60`）：`implosion` 场景声明 `"metric": "warm"`，
  于是它的「空场起飞 / 真的渲染 / 退场」都用暖色曲线判（其余场景仍用蓝）；
- **暗核心判据**（亮度 `0.299r + 0.587g + 0.114b < 40`）：内爆的**黑色核心球**在中灰棚里是唯一的暗区。

**跑流水线的两条硬规矩（第 29 轮补，都是为了不浪费时间）**

1. **一律后台跑**：`capture.ps1` 会开真客户端，正常约 1~3 分钟，但**客户端崩溃时不会自己退出**
   （第 29 轮实测：`FMLClientSetupEvent` 里碰 `RenderSystem` 抛 `Rendersystem called from wrong thread`，
   游戏停在错误界面不动，前台调用把整轮对话挂死）。所以用后台任务跑，跑完再看结果。
2. **脚本自带超时护栏**：`-TimeoutSec`（默认 300 秒）到点就杀掉 `bootstraplauncher|devlaunch|net.minecraft`
   那个 java 进程、打印 `run/capture/logs/latest.log` 里的前 8 条异常并以失败退出。
   想改等待上限就传 `-TimeoutSec 600`（大场景多的时候），**不要**去掉这个护栏。

⚠️ `implosion` 场景**故意用中灰摄影棚**（`buildStudio(..., color = LIGHT_GRAY)`）：
黑球在原来的黑底棚里根本看不见，而中灰（`r≈g≈b`）既不参与蓝判据也不参与暖色判据。
别退回「红 > 蓝」那种粗判据：木桩与木构件是棕色 (135,109,68)、火球拖尾是橙色 (219,122,21)，
两者都能通过粗判据（实测会把破口早就没了的末帧报成上万像素）。

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


### 9.4 本机录制问题（2026-10-08）

- `assemble.py` 控制台统一 UTF-8，日志损坏字符不会再导致 GBK 打印异常。
- 若场景 PASS、总体仍 FAIL，检查报告最后的运行日志。当前本机 OSHI 的 Windows 性能计数器查询失败，
  随后有进程信息空指针，已登记到 `issues/open.md`；不要关闭日志异常门禁以冒充全部通过。
- 第 49–51 轮黑洞/重力场模型与预算见 [design/field-effects.md](design/field-effects.md)。


### 9.5 第52轮内容验收

`pwsh tools/capture/capture.ps1 -Scene expansion_crystals,expansion_arc,expansion_plasma,expansion_utilities,expansion_combinations`
分别录140/150/145/220/180tick；内容与断言见 [design/spell-expansion.md](design/spell-expansion.md)。
场景公共准备清除前一场的内部遮挡墙，保留摄影棚；斥力探针的箭矢与魔法弹分开出生，避免互相碰撞。
等离子束使用侧面机位，避免正面沿束轴拍摄只看到一点；末尾检查投射物与PD粒子数为零。
图标资源重建：`python tools/spells/generate_icons.py`（需要Pillow）。

电弧与护盾混合场景用游戏tick8–64的独立电弧窗口量可见性及渐隐，保留全局最低像素门槛，
避免护盾玻璃峰值掩盖电弧。组合场景使用木桩作为移动施法者，录制相机不会覆盖其位置。


### 9.6 第53轮全量MC风格验收

执行 `pwsh tools/capture/capture.ps1 -Scene all`，覆盖12条已有真实施法场景。
本轮没有透明度渐隐：像素覆盖衰减来自尺寸收缩与错峰寿命，报告标签同步调整，最低可见性/零残留门槛保留。
44张法术卡图标重建及16px预览见 [design/minecraft-visuals.md](design/minecraft-visuals.md)。


### 9.7 第54轮质感与法术联动

全量录制新增spell_resonance（320tick），包含448组配方/继承检查与真实晶体、冰火、吞弹、反射、集束验收。
等离子束延寿判据改为40tick后仍活，墙挡测试前结束前一束，保留独立无伤害断言。
爆炸即时预算按新喷射/火团/冲击圈检查1200–2200，仍在age=1死亡触发观察伤害。
设计及结果见 [design/spell-quality.md](design/spell-quality.md)。

最终13场证据为12场全量加spell_resonance定向复验，合并2309帧/零丢帧/81项断言通过；
视频与帧仍在build/capture/out，来源记录runs.tsv，完整结果与既有OSHI日志限制见spell-quality。


### 9.8 第55轮稳定性回归

新增spell_stability（180tick）：短时双黑洞互吸携带共同前向速度穿过石墙（t45结束以隔离已确认的PD长时断线），首tick追踪、统一1.6倍初速、穿刺弹穿墙积分、晶体粒子预算与清理。
PD批量速度超过512成员的断线已在客户端日志确认；当前前置存在此缺陷，不能把双场短时PASS当成长时稳定修复。
修复前置后须复验至少120tick移动星尘（覆盖60tick粒子寿命积累）和513/1024成员批量更新。


### 9.9 第56轮持杖法阵

runCapture -Pcapture=black_hole,black_hole_moving,spell_resonance,wand_casting,wand_casting_first_person录制移除黑洞环和真实持杖流程。
wand_casting为220tick，第三人称取景；CameraType现在真正按Scene.thirdPerson切换。预热阶段测试法杖放入背包，
避免WandManager回收后测试继续调用已unload的旧引用；这是测试夹具生命周期要求。
玩法、视觉与清理口径见design/wand-casting.md。


黑洞环移除后，移动像素检查取固定边界星点色板，保留原游戏tick与60px双向位移门槛；不用已删除的内盘色板判空。


### 9.10 第57轮：真实法杖模型与退场

执行gradlew runCapture -Pcapture=wand_casting,wand_casting_first_person，配合§9的后台启动与300秒超时护栏；合成时只纳入本次重新录制的场景，勿将上次帧标为本次结果。
两场各220tick；第一视角showHands=true，保持HUD状态为可见以让MC渲染手持物品，随后仅取消RenderGuiEvent.Pre绘制UI。第三视角不变。
退场改为t187–216有限解体，50%→5%至少4帧；玩法检查验证放下后粒子仍短暂保留、16tick后归零。随机汇聚、白色间隔退场、琥珀充能收束和宝石发射需检查真实PNG/GIF，不只看服务端粒子数。

宽视野复验额外录wand_casting_wide（90°，220tick），默认第一视角60°；青白专属像素判据排除饱和蓝宝石，不把模型消失当成粒子退场。录制同时关闭原版教程气泡，避免UI遮住宝石。

### 第58轮持杖取景

新增wand_aim验证主副手近远命中和遮挡；wand_casting_front使用Scene.thirdPersonFront展示正面握柄。正面抬头取景的弹体比俯视取景飞行更久，夹具在完成真实出生点检查后提前结束弹体，使158/208tick的归零断言只衡量持杖退场；219tick仍独立验证全部投射物及PD粒子归零。
装配器只读取当前场景实际使用的色域指标，不改变掩码阈值和验收门槛；持杖场景新增缺失材质警告检查。

### 第59轮低模法杖与idle回归

录制 `-Pcapture=spell_resonance,wand_casting,wand_casting_first_person,wand_casting_wide,wand_casting_front,wand_aim`。
法杖新增主副手原版itemUsed高度回调与空手对照；60°/90°视角按两枚符印专属色域逐帧检查，排除模型和焦点掩盖断档。充能柱只读脚下坐标，t65–77独立观察完整收尾，t78再重复施法，避免效果重叠污染断言。
在当前录制之前遗留其它场景目录时，不把历史帧当成本轮证据；仅装配本轮6个目录（可在build/capture/round59out放6个指向out对应目录的Junction，再 `python tools/capture/assemble.py --out build/capture/round59out`）。
结果回填design/wand-casting.md；运行日志异常门禁保持。

### 第60轮模块/状态时序验收

选择black_hole,black_hole_moving,spell_resonance,wand_casting,wand_casting_first_person,wand_casting_wide,wand_casting_front,wand_aim。
客户端场景采样原生模型属性验证待机变化、主副手施法后亮态和300ms后复原；像素门禁验证t29（施法下一tick）两种冷却已经可见。短退场适配本轮要求，具体门槛见design/wand-casting.md。
本轮独立装配目录build/capture/round60out仅包含8个对应场景的Junction，不把旧场景媒体计为新证据。日志异常门禁不变。

### 第61轮动画连续性验收

`./gradlew build --console=plain`；后台`runCapture -Pcapture=all`覆盖19场。
镜头纯曲线验证：按§7使用K2JVMCompiler编译`client/CameraImpulse.kt`与`tools/verify/CameraImpulseCheck.kt`，运行`CameraImpulseCheckKt`。
当前实机原始证据`build/capture/final61b.log`、`final61c.log`、`final61d.log`、`summary61b.tsv`、`summary61c.tsv`、`summary61d.tsv`、`runs61.tsv`及`out/`；不能关闭OSHI日志异常门禁。
连续性设计、真实边界与结果见[animation-continuity.md](design/animation-continuity.md)。


### 第62轮：前置源码编译与连续性复验

先在`D:/Backup/IDEA Projects/ParticleDrawing`运行`gradlew.bat build sourcesJar --console=plain`；将build/libs的1.0.15-ALPHA主JAR、sources JAR复制到本项目lib，仅保留当前版本。
改gradle.properties的版本与带ALPHA的版本下限后，运行`gradlew.bat build --no-build-cache --console=plain`。
前置自身测试使用`gradlew.bat test --rerun-tasks --console=plain`，322项通过。
runCapture仍须隐藏后台启动与300秒超时护栏。全量覆盖19场，稳定性场景t128检查两洞运行120tick，t92建立1024成员并执行一次setVelocityAll，t96检查保持连接。
ParticleManager.spawnAll公开上限256与运动载荷上限是不同口径；1024成员测试用ParticleBatch.ensureSize建立集合，速度仍一次交给PD自动拆包。
法杖第一人称最后俯视地面阵列，用实际视野检查退场；火球摄影棚清除前场遗留墙，禁止把墙体遮挡当成生命周期收尾。


第62轮最终来源为round62c的14场与round62f的5场补录；runs62.tsv记录来源，summary62.tsv汇总3529帧/零丢帧/170项断言。不得把中间接线失败/视觉失败的62a–e当成最终证据。
前置组尺寸/变量仍20Hz求值的源码限制单独登记，新API接线通过不等于严格高刷保证已经完成。

## 第63轮验收范围

全量录制新增`spell_mechanics`（220刻），其余19场保留。爆炸判据改为暖色，与随机橙黄火舌匹配；去除已删除的蓝色外环依赖，最低可见性/空场/零残留门禁保留。电弧断言改为第五次衰减命中；静止光束墙挡阶段清理上一阶段尚在显示的浮字；集束夹具改为弹跳复制的独立配方继承。环绕范围判据与增大的半径同步。

第63轮最终来源为63b的14场与63c的6场补录；`build/capture/out/runs63.tsv`逐场记录来源。20场共3749帧、零丢帧、196项玩法断言全部通过；新增机制场26项全部通过。构建通过，逐场视觉门禁通过。总体报告仍被本机OSHI的5条异常阻断，不能声明流水线整体通过；该环境问题独立登记在issues/open.md §3.1。

## OSHI本机计数器排障（2026-10-09，尚未验证修复）

OSHI是Minecraft自带的操作系统/硬件信息库。此处异常发生在CrashReport.preload收集系统报告时，早于法术初始化；当前问题是Windows性能计数器查询失败后当前进程返回null，并非黑洞结算异常。

官方流程：[Microsoft计数器重建](https://learn.microsoft.com/en-us/troubleshoot/windows-server/performance/manually-rebuild-performance-counters)、[lodctr参数](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/lodctr)、[OSHI配置说明](https://www.oshi.ooo/xref/oshi/util/GlobalConfig.html)。`oshi.os.windows.hkeyperfdata=false`是受支持的备用路径，但本机仍缺少当前PID，不能采用该配置冒充修好。

管理员PowerShell可执行`tools/verify/RepairPerformanceCounters.ps1`；指定`-RestoreFrom <本机备份INI>`可显式还原，`-RestartServices`执行微软流程中的查询服务重启。脚本不会自动重启电脑。每次先备份到`build/verify/oshi/backup-时间戳`，输出result.txt；必须看复查结果，不能只看lodctr退出码。生产模组不注入/替换OSHI、不隐藏硬件采集错误。

本次已重建两种位宽、同步WMI、修正PerfOS错误值类型；英文Counter仍错误87，OSHI仍返回null，Windows拒绝重启WMI服务。待用户重启后复验。通过条件：OSHI当前PID非null、CPU时间/物理与虚拟内存为真实有效数据；新runCapture日志无对应异常，总体日志门禁通过。

修复尝试后63d重新启动客户端并补录spell_mechanics：220帧/零丢帧/26项全通过；`build/capture/final63d.log`仍含同样5条OSHI异常，PerfOS字段类型警告已消失。用户选择稍后手动保存工作并重启，禁止本轮自动重启；重启后的系统/录制复验尚未进行。

## 第64轮验收范围（2026-10-09）

最终8场：`spell_timing`、`spell_mechanics`、`expansion_arc`、`expansion_combinations`、`expansion_crystals`、`crystal_lifecycle`、`spell_resonance`、`spell_stability`。1520帧、零丢帧、79/79项玩法断言通过，逐场帧数/像素可见性/退场与媒体门禁通过。新增延迟/链锯场12项，水晶生命周期场7项；真实施法、伤害、牌序与清理均走原运行时。

最终画廊`build/capture/final64/index.html`，报告`report.txt`，逐场来源`runs.tsv`，帧/断言统计`summary.tsv`；该独立目录只含本轮8场，不混入此前录制。7场来自64b，延迟/链锯场来自最终64c；原日志分别存于`build/capture/final64b.log`与`final64c.log`。64a另补录第一人称施法220帧/16项通过，未计入上述8场总数。

人工检查水晶四个外侧视角的填充面、白闪及到期真实爆炸；延迟载体释放前收拢、释放后与荷载同位置飞散；基础及延寿加速链锯切割均可见。图标仍用现有像素生成器，最终43张卡。

总体报告仍FAIL：新客户端启动仍含既有OSHI/Perflib/JNA的5条异常，没有调整日志门禁或隐藏错误。用户尚待手动重启，法术验收通过不能作为系统计数器恢复的证明。

新增两场的退场判据使用已有`collapse`门禁（50%→5%至少4帧），分别实测5/7帧；初版夹具误用“瞬间消失≤3帧”口径，已按晶体爆炸/锯齿飞散语义修正。最低可见性、空场、零残留和日志异常门禁未放宽。

## 第65轮验收范围（2026-10-09）

最终65f录制十场：black_hole_moving、explosion、expansion_crystals、expansion_arc、expansion_plasma、spell_resonance、spell_stability、spell_mechanics、crystal_lifecycle、combat_impact。1781帧、零丢帧，93/93项玩法检查通过。新增combat_impact的18项使用真正原版猪检查受伤/推力/抗性/自伤/暗核，另覆盖空场四跳、最近两次避让、薄墙相位、实心落点停止、延寿11跳和跃迁触发易爆修正。

最终独立画廊`build/capture/final65/index.html`，报告`report.txt`，统计`summary.tsv`，逐场来源`runs.tsv`全部为65f；原日志`build/capture/final65f.log`。先前65a失败夹具及65b/c中间结果均不作为最终证据；最终长片元已改用PD alignTo径向对齐，保留原始PNG供检查。

玩法门禁不因重构放宽：爆炸首拍1200～2200颗、所有场景原有像素最低覆盖/空场/退场/媒体/零PD残留规则保留。电弧四次伤害从旧逐跳衰减改为四次真实4点放电，与最终Noita机制一致。总体日志仍被既有OSHI的5条异常阻断，未把场景PASS写成系统环境恢复。

65f十场视觉及媒体门禁全部通过，大爆炸暖色覆盖50%→5%跨4帧（原门槛≥4）。65d/e退场失败均不作为最终验收；最终只使用65f新客户端完整十场。开发录制setup清理邻近原版掉落物，防止伤害测试的猪肉污染后续空场；不清除或过滤粒子。构建通过；总体报告仍因既有5条OSHI异常FAIL。

## 第66轮验收范围（2026-10-09）

新增damage_mass场，25项真实原版生物检查覆盖8类伤害源/死亡组件/no_knockback、火/冰/爆炸原版标签、法术分派、冰冻转换、爆炸独立类型、1刻持续伤害、质量安全值和八倍惯性、双黑洞长时慢速互吸。8类名称与3种死亡变体的中英键及数据包一致性另做资源检查。

最终独立目录`build/capture/final66/`，12场2286帧、零丢帧、128/128项玩法通过；`runs.tsv`记录明确来源：10场66a，等离子场66c，爆炸场66d；原日志分别`final66a/c/d.log`。未采用66a/b等离子旧护盾断言及未收尾的结果。等离子场改为前几次被盾吸收、20刻预算耗尽后击穿并冻结两条检查，时长210刻等待原有破口生命周期走完。

爆炸本轮未改表现参数：原12格镜头在随机火舌最远约18格的内部，火舌掠过镜头会放大再退出屏幕，被覆盖量误判为硬切。仅验收机位移至20格，完整记录外扩与燃尽；原可见性、50%→5%至少4帧、零残留门禁均保留，最终跨度5帧。光照残留是PD缓存缺陷，源码诊断见pd-api-requests第66轮，尚未进行修复闭环。

构建通过。OSHI/Perflib的既有5条启动异常仍阻断总体日志门禁，本轮不重启或过滤日志；用户手动重启后的系统复验仍待进行。

最终12场的帧数/像素可见性/空场/连续退场/零残留/媒体门禁全部PASS；原版伤害分类木桩文字在expansion_arc帧20可见，双黑洞速度证据在damage_mass meta的t70/t140。总体FAIL仅包含既有5条OSHI启动异常。


## 第67轮验收范围（2026-10-09）

最终12场2470帧、零丢帧、147/147项玩法断言通过。十场既有玩法来自67a，新增recoil_light与concentrated_light_first_person来自最终67g；来源逐场记录在`build/capture/final67/runs.tsv`，原日志`final67a.log`、`final67g.log`。独立画廊`final67/index.html`、报告`report.txt`、帧/检查统计`summary.tsv`。67a新场的射程夹具失败及67b～f视觉中间结果不作为最终证据。

新增22项检查覆盖付费真实施法的基础反冲、两张卡/顺序/叠加、子弹不重复反冲、蓄能后一次反冲、真实原版猪36点能量伤害、质量4%径向阻尼、创造飞行/落地、20刻禁止提前发射、148.8格纯运动距离、真实墙面只反弹一次后终止、真实法杖转向时蓄能跟随及20刻释放、最终零投射物/零PD残留。距离项单独关闭碰撞以隔离studio木桩，伤害与墙反弹另走未绕过的Move碰撞；没有修改玩法碰撞。配方矩阵自动扩为18×28=504组合。

世界录制使用侧面机位/两堵墙，独立70～110刻窗口覆盖正常蓄能后的射出、一次反弹、第二次碰墙终止及余辉，避免手工检查产生的粒子污染退场指标。第一人称用上方300像素内暖色衡量杖首，排除原版裸手肤色；保留相同737像素最低可见性、空场、50%→5%至少4帧、零PD残留门槛，最终两场收尾跨度14/12帧。人工检查聚焦棱片、白光脊/金侧线、第一人称准星区域和宝石释放亮态。

最终构建通过，所有12场玩法/视觉/媒体门禁通过。总体日志仍FAIL，仅含既有OSHI/Perflib/JNA的5条启动异常；用户尚待手动重启，不隐藏日志、不声称系统环境已修复。新卡资源JSON和中英键完整性检查通过，图标生成器共46卡。


## 第68轮验收范围（2026-10-09）

最终8场2181帧、7帧丢帧（均来自250刻shield_balance，在原5%/12帧容限内）、114/114项玩法通过。来源：expansion_plasma/spell_resonance/spell_mechanics/combat_impact为68a；energy_shield/shield_balance为68b；recoil_light/concentrated_light_first_person为68d。独立产物build/capture/final68，逐场来源runs.tsv、玩法summary.tsv、原日志final68a/b/d.log。未采用68a旧护盾伤害断言、过早取样的第一人称距离断言，以及68b只允许单次反弹的固定时刻检查。真实双墙手动推进精确检查五次反弹后终止；运行时检查记录剩余反弹数与速度，不假定某个场景时刻必定仅反弹一次。

ShieldBalanceScene新增16项原版受伤/法力检查：1卡和8卡预算、20发144强度、零MP破盾、破盾首发及后续投射物/爆炸、同载体易爆不二次伤害、旁人爆炸、穿刺、内部爆炸、真实碰撞单卡破/八卡完整、零MP八卡破，以及假人push与100伤害爆炸免推力；结束零投射物/零PD残留。第一人称实持法杖并走动转身，t45世界坐标固定、t50仍沿出手方向且releaseAge=20。

第68a视觉发现trackAll+sizeCurve冻结首帧尺寸，锁定sources及外部PD源码诊断一致；登记pd-api-requests第68轮。最终固定镜阵只用已有原生move一次性合拢，无本侧插值或渲染替代。第68d近景已缩到半径0.30格与1/16格普通片元，检查第32/43帧聚光图案及准星区域。构建及语言JSON通过。总体日志仍有既有5条OSHI启动异常，手动重启复验尚待用户完成；没有隐藏日志或宣称系统恢复。

最终8场玩法/帧数/像素可见性/空场/收尾/媒体门禁全部PASS；汇聚世界/第一人称50%→5%分别13/22帧，护盾对比10帧。总体报告仅因既有5条OSHI启动异常FAIL。画廊index.html及report.txt已生成并人工检查第一人称接触表和护盾对比原始帧。


## 第69轮验收范围（2026-10-09）

只复验recoil_light、concentrated_light_first_person两场，最终69a共394帧、6丢帧（分别2/11与4/9，均在原容限），22/22玩法检查通过。原版猪蓄能释放前后速度均0，且加入0.35反冲加值仍不推动；其它法术的基础反冲、修正顺序与叠加检查仍通过。20刻释放、锁定方向、5次真实墙反弹及终止、零投射物/零PD残留保持。

独立画廊build/capture/final69/index.html，报告report.txt、逐场来源runs.tsv均为69a，原日志final69a.log。人工查看第一人称释放后49/52帧及连续图，镜阵后震、张开与回弹在原部署点连续发生。两场帧数/玩法/像素可见性/空场/退场/媒体门禁全部PASS；50%→5%分别11/21帧。构建通过，中英描述同步纯视觉反冲。总体报告仍只因既有5条OSHI启动异常FAIL，未进行系统重启或隐藏日志。


## 第70轮验收范围（2026-10-09）

最终8场2180帧、8丢帧（均shield_balance，250刻原容限12帧内），128/128玩法通过。energy_shield/expansion_plasma/spell_resonance/spell_mechanics/combat_impact/concentrated_light_first_person来自70a；shield_balance来自70b；recoil_light来自70c。独立目录build/capture/final70，来源runs.tsv、统计summary.tsv，原日志final70a/b/c.log。

ShieldBalanceScene增加12项真实Move高速护盾检查：4.8、24、64格/刻正面及12格/刻斜入射，每组验证反弹后存活/实体命中0/墙反弹仍5/持盾原版猪1000生命/护盾未破、被动再扫只付一次5MP、反射后实际另一实体受36伤害。另增1项连锁魔弹真实跃迁反射方向/慢漂移及MP检查，总29项。RecoilLightScene增加真实付费施法的0.06轻反冲，改蓄能队列为出手立即轻推且释放不再推，总19项。付费测例的光束在出手检查后立即结束蓄能，避免污染70～110刻独立发射镜头。

70a的64格夹具出生在摄影棚后墙之外，先碰墙，不能据此判断护盾；70b只把出生点改在墙内，仍用64格/刻原生Move，不关闭任何碰撞。70b世界场的额外付费测试光束未隔离，最终仅采用70c该场；其余镜头/碰撞/像素/收尾门槛保留。最终构建和中英语言JSON检查通过。总体日志仍包含既有5条OSHI启动异常，用户手动重启复验未进行，未过滤或宣称修复。

最终8场玩法/帧数/可见性/空场/收尾/媒体门禁全部PASS，世界聚光12帧、第一人称21帧、护盾对比10帧收尾跨度。人工检查护盾对比原始帧和世界聚光接触表；总体FAIL仅含既有5条OSHI启动异常。
