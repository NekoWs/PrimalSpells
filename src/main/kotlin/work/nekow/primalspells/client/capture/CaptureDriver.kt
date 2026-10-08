package work.nekow.primalspells.client.capture

import net.minecraft.client.Minecraft
import net.minecraft.client.CameraType
import net.minecraft.client.Screenshot
import net.minecraft.client.tutorial.TutorialSteps
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ParticleStatus
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Util
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.common.NeoForge
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import work.nekow.primalspells.entity.TestDummyEntity
import work.nekow.primalspells.magic.core.MagicManager
import work.nekow.primalspells.registry.ModEntities
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * 实机录制驱动 —— 「进游戏把真实表现录下来」的那台摄像机。
 *
 * ## 为什么需要它
 *
 * 离线自检（`docs/build-and-run.md` 第 7 节）只能验数值/几何，验不了「粒子在屏幕上长什么样」；
 * 而实机录制要四件手工操作：开客户端 → 进世界 → 摆位置施法 → 按 F2 截图。
 * 本类把这一串全部自动化，产出**逐 tick 的 PNG 帧序列**，交给 `tools/capture/assemble.py`
 * 合成 mp4/gif/拼图，于是「看一眼实际效果」不再需要人动手。
 *
 * ## 生命周期（客户端线程一步一步推）
 *
 * ```
 * 启动 → 配置画面（隐藏 HUD / 关失焦暂停 / 静音）
 *      → 存档不存在就现场建一个超平坦世界（WorldOpenFlows.createFreshLevel）
 *      → 逐场景：预热 → prepare（布景 / 清残留 / 摆机位）→ 录 length 帧 → checks → meta
 *      → 全部跑完 → mc.stop() 自动退出
 * ```
 *
 * ## 抓帧口径：**每个 tick 恰好一帧**
 *
 * 客户端渲染帧率远高于 20Hz，逐帧全录会得到上千张重复帧。这里的口径是：
 * 客户端 tick 时记下「本 tick 还没抓」，渲染线程在**本 tick 的第一帧**抓一次 ——
 * 输出恰好 20fps、每 tick 一帧、无重复、无抖动，与游戏的实际动画步进一致。
 *
 * ## 只装在开发环境
 *
 * [shouldInstall] 要求「非生产环境 + 显式系统属性」；生产 jar 里本类不会被加载，
 * 也不会注册任何监听器（注册入口在 `PrimalSpellsClient`）。
 */
object CaptureDriver {

    /** 开关：`all` 或逗号分隔的场景名（由 `build.gradle` 的 `capture` 运行配置注入）。 */
    const val PROP_ENABLE = "primalspells.capture"

    /** 输出根目录（PNG 帧、meta、日志都写这里）。 */
    const val PROP_OUT = "primalspells.capture.out"

    /**
     * `true` = 录制时**保留客户端窗口可见**（调机位/调场景时用；默认隐藏，见 [hideWindow]）。
     */
    const val PROP_SHOW_WINDOW = "primalspells.capture.showWindow"

    /** 录制世界 id（`run/capture/saves/<本值>`）；`tools/capture/capture.ps1` 每次跑前会删掉它。 */
    const val LEVEL_ID = "primalspells-capture"

    /** 超平坦世界的固定种子：地面高度/群系每次一致。 */
    private const val SEED = 20260930L

    /** 整轮录制的安全阀（客户端 tick）：跑满无条件退出，避免卡在某个场景里空转。 */
    private const val HARD_TIMEOUT_TICKS = 20 * 60 * 8

    /** 同时在写的帧数上限：超过就跳帧（宁可少一帧，也不要堆积几百 MB 内存）。 */
    private const val MAX_INFLIGHT_WRITES = 24

    /** 跑完后退出的等待 tick 数（等尾部几帧写完）。 */
    private const val STOP_GRACE_TICKS = 20

    private val LOG = LoggerFactory.getLogger("primalspells-capture")

    // ---------------------------------------------------------------- 状态

    private var installed = false
    private var abortRequested = false
    private var booted = false
    private var worldRequested = false

    private var scenes: List<Scene> = emptyList()
    private var sceneIndex = -1
    private var scene: Scene? = null

    /** 当前场景内的相对 tick：负数 = 预热（不录），>= 0 = 正式录制。 */
    private var tickInScene = 0

    /** 整轮已跑的客户端 tick（安全阀用）。 */
    private var totalTicks = 0

    /** 全部场景跑完后的收尾倒计时（等 PNG 写盘），> 0 表示正在收尾。 */
    private var stopDelayTicks = 0

    /** 渲染线程要抓的 tick；抓到后置回 -1。 */
    @Volatile
    private var pendingCaptureTick = -1

    @Volatile
    private var captureArmed = false

    @Volatile
    private var lastCapturedTick = -2

    private val inflightWrites = AtomicInteger(0)

    private val frameLines = StringBuilder()
    private val checkLog = ArrayList<Check>()
    private var frameIndex = 0
    private var framesDropped = 0
    private var sceneLog = StringBuilder()
    private val results = ArrayList<SceneResult>()

    private val outRoot: File
        get() = File(System.getProperty(PROP_OUT) ?: "build/capture/out")

    /** 仅开发环境 + 显式系统属性：生产包里这个类根本不会被触碰。 */
    fun shouldInstall(): Boolean =
        !FMLEnvironment.isProduction() && !System.getProperty(PROP_ENABLE).isNullOrBlank()

    /** 注册监听器（由 `PrimalSpellsClient` 在满足 [shouldInstall] 时调用）。 */
    fun install() {
        if (installed) return
        installed = true
        val wanted = System.getProperty(PROP_ENABLE)?.trim().orEmpty()
        scenes = CaptureScenes.all().filter { wanted == "all" || it.name in wanted.split(',') }
        NeoForge.EVENT_BUS.addListener<ClientTickEvent.Post> { onClientTick() }
        NeoForge.EVENT_BUS.addListener<RenderFrameEvent.Post> { onRenderFrame() }
        // MC的隐藏HUD同时隐藏第一人称物品；模型场景只取消GUI绘制，保留真正的持物渲染。
        NeoForge.EVENT_BUS.addListener<RenderGuiEvent.Pre> { if (scene?.showHands == true) it.isCanceled = true }
        log("install: value=$wanted out=${outRoot.absolutePath} scenes=${scenes.map { it.name }}")
        if (scenes.isEmpty()) abortRequested = true
    }

    // ---------------------------------------------------------------- 客户端 tick：状态机

    private fun onClientTick() {
        val mc = Minecraft.getInstance()
        // 兜底：录制期间不需要鼠标，抓住就放掉（正常情况下窗口已被隐藏，根本抓不到）
        runCatching { if (mc.mouseHandler.isMouseGrabbed) mc.mouseHandler.releaseMouse() }
        totalTicks++
        if (abortRequested) {
            log("no scene to record —— 退出")
            captureArmed = false
            mc.stop()
            return
        }
        if (totalTicks > HARD_TIMEOUT_TICKS) {
            log("HARD TIMEOUT after $totalTicks client ticks —— 强制退出")
            captureArmed = false
            mc.stop()
            return
        }
        if (stopDelayTicks > 0) {
            // 收尾：等最后一帧落盘再退出（PNG 写盘在 IO 线程上，提前 stop 会截断尾部帧）
            stopDelayTicks--
            if (stopDelayTicks <= 0) {
                if (inflightWrites.get() > 0) {
                    stopDelayTicks = 1 // 还有帧在写：再等一 tick
                } else {
                    log("stopping client (all frames flushed)")
                    mc.stop()
                }
            }
            return
        }
        if (!booted) {
            booted = true
            configureClient(mc)
        }
        // 世界没加载：请求建/开世界，等加载完再来
        if (mc.level == null || mc.player == null) {
            ensureWorld(mc)
            return
        }
        // 加载界面 / 暂停菜单期间不推进（加载中 screen 非空）
        if (mc.gui.screen() != null) return

        val current = scene
        if (current == null) {
            beginNextScene(mc)
            return
        }
        stepScene(mc, current)
    }

    /**
     * **不让录制窗口抢使用方的鼠标**（无人值守录制的底线，实机反馈过这个问题）。
     *
     * 1. `GLFW.glfwHideWindow` 把窗口直接隐藏，并关掉 `FOCUS_ON_SHOW` —— 窗口不出现、拿不到焦点；
     * 2. 每 tick 兜底 `MouseHandler.releaseMouse()`（见 [onClientTick]）—— 万一窗口还是被显示出来，
     *    鼠标也不会被锁进游戏里。
     *
     * 隐藏只影响「窗口是否呈现」：渲染循环与帧缓冲照旧，而 [Screenshot.takeScreenshot] 读的是
     * `gameRenderer.mainRenderTarget()`，与窗口是否可见无关（每次录制都会用「特效确实渲染」这条断言兜底验证）。
     * 想看着录制跑（调机位/调场景时有用）：`-PcaptureShowWindow=true`。
     */
    private fun hideWindow(mc: Minecraft) {
        if (System.getProperty(PROP_SHOW_WINDOW).toBoolean()) {
            log("showWindow=true：保留客户端窗口（调试模式）")
            return
        }
        runCatching {
            val handle = mc.window.handle()
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE)
            GLFW.glfwHideWindow(handle)
            log("client window hidden (不抢焦点/鼠标；-PcaptureShowWindow=true 可保留窗口)")
        }.onFailure { log("hideWindow failed: ${it.message}") }
    }

    /** 一次性画面配置：把我们不想要的一切关掉，让输出里只剩特效本身。 */
    private fun configureClient(mc: Minecraft) {
        hideWindow(mc)
        // 隐藏窗口在部分 Windows 驱动上返回 0×0，Window 会兜底成 1×1。
        // 录制走离屏目标，明确保持请求的分辨率，不能把缩成一像素的帧当成证据。
        mc.window.setWidth(1280)
        mc.window.setHeight(720)
        mc.framebufferSizeChanged()
        mc.tutorial.setStep(TutorialSteps.NONE)
        mc.options.pauseOnLostFocus = false
        mc.options.bobView().set(false)
        mc.options.fovEffectScale().set(0.0)
        mc.options.particles().set(ParticleStatus.ALL)
        mc.options.enableVsync().set(false)
        mc.options.framerateLimit().set(120)
        mc.options.renderDistance().set(8)
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0)
        if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle()
        log("configured: hideHud=${mc.gui.hud.isHidden()} pauseOnLostFocus=${mc.options.pauseOnLostFocus}")
    }

    /** 存档不存在就现场建一个超平坦世界（`tools/capture/capture.ps1` 每次跑前会删掉旧存档）。 */
    private fun ensureWorld(mc: Minecraft) {
        if (worldRequested) return
        worldRequested = true
        val save = File(mc.gameDirectory, "saves/$LEVEL_ID")
        if (save.isDirectory) {
            log("open existing world: $LEVEL_ID")
            mc.createWorldOpenFlows().openWorld(LEVEL_ID) { log("openWorld cancelled") }
            return
        }
        log("create fresh flat world: $LEVEL_ID (seed=$SEED)")
        val settings = LevelSettings(
            LEVEL_ID,
            GameType.CREATIVE,
            LevelSettings.DifficultySettings.DEFAULT,
            true,
            WorldDataConfiguration.DEFAULT,
        )
        val options = WorldOptions(SEED, false, false)
        mc.createWorldOpenFlows().createFreshLevel(
            LEVEL_ID,
            settings,
            options,
            { registries ->
                registries.lookupOrThrow(Registries.WORLD_PRESET)
                    .getOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
            },
            // parentScreen 只在「建世界失败」时用于回退显示，这里给一个标题界面即可
            mc.gui.screen() ?: net.minecraft.client.gui.screens.TitleScreen(),
        )
    }

    // ---------------------------------------------------------------- 场景推进

    private fun beginNextScene(mc: Minecraft) {
        sceneIndex++
        if (sceneIndex >= scenes.size) {
            finishAll(mc)
            return
        }
        val next = scenes[sceneIndex]
        val server = mc.singleplayerServer ?: return
        scene = next
        tickInScene = -next.warmupTicks
        captureArmed = false
        pendingCaptureTick = -1
        lastCapturedTick = -2
        frameIndex = 0
        framesDropped = 0
        frameLines.clear()
        checkLog.clear()
        sceneLog = StringBuilder()
        next.framesDir.mkdirs()
        next.framesDir.listFiles()?.forEach { it.delete() }
        sceneLog.appendLine("scene\t${next.name}")
        sceneLog.appendLine("title\t${next.title}")
        sceneLog.appendLine("width\t${mc.window.width}")
        sceneLog.appendLine("height\t${mc.window.height}")
        sceneLog.appendLine("fps\t20")
        sceneLog.appendLine("lengthTicks\t${next.lengthTicks}")
        sceneLog.appendLine("warmupTicks\t${next.warmupTicks}")
        sceneLog.appendLine("thirdPerson\t${next.thirdPerson}")
        sceneLog.appendLine("showHands\t${next.showHands}")
        if (mc.gui.hud.isHidden() == next.showHands) mc.gui.hud.toggle()
        mc.options.fov().set(next.fov)
        mc.options.cameraType = if (next.thirdPersonFront) CameraType.THIRD_PERSON_FRONT
            else if (next.thirdPerson) CameraType.THIRD_PERSON_BACK else CameraType.FIRST_PERSON
        log("=== scene ${next.name} start (warmup=${next.warmupTicks}, length=${next.lengthTicks}) ===")
        server.execute {
            inServer(next.name, "prepare") { next.prepare(ctx(server)) }
        }
    }

    private fun stepScene(mc: Minecraft, current: Scene) {
        val server = mc.singleplayerServer ?: return
        val t = tickInScene
        // 收尾**延后一 tick**：最后一帧要在下一个渲染帧才抓得到，
        // 若在本 tick 立刻 captureArmed=false，末尾的 tick 就永远录不到（少一帧）
        if (t >= current.lengthTicks) {
            endScene(mc, current)
            return
        }
        tickInScene = t + 1
        val recording = t >= 0
        if (recording) captureArmed = true
        server.execute {
            inServer(current.name, "tick $t") {
                val context = ctx(server)
                if (recording) {
                    if (t == 0) current.start(context)
                    current.tick(context, t)
                    checkLog += current.checksAt(context, t)
                }
                // 机位：服务端权威（客户端跟着传送包走，两端不会互相覆盖产生抖动）
                val pose = current.camera(context, if (recording) t else 0)
                if (current.moveCamera) {
                    context.player.connection.teleport(pose.x, pose.y, pose.z, pose.yaw, pose.pitch)
                } else {
                    val p = context.player
                    context.player.connection.teleport(p.x, p.y, p.z, pose.yaw, pose.pitch)
                }
            }
        }
        if (!recording) return
        // 上一 tick 的帧还没被渲染线程取走就又被覆盖 = 这一 tick 根本没渲染出帧（帧率掉到 20fps 以下）。
        // 不记这一笔的话，产出会静默少几帧、视频里凭空少一段动画。
        if (pendingCaptureTick >= 0 && pendingCaptureTick != lastCapturedTick) framesDropped++
        pendingCaptureTick = t
    }

    private fun endScene(mc: Minecraft, current: Scene) {
        captureArmed = false
        pendingCaptureTick = -1
        mc.singleplayerServer?.let { server ->
            server.execute { inServer(current.name, "finish") { current.finish(ctx(server)) } }
        }
        val failed = checkLog.count { !it.ok }
        checkLog.forEach { log("CHECK ${it.render()}") }
        results += SceneResult(current.name, frameIndex, framesDropped, checkLog.size, failed)
        writeSceneFiles(current, failed)
        log("=== scene ${current.name} done: frames=$frameIndex dropped=$framesDropped checks=${checkLog.size} failed=$failed ===")
        scene = null
    }

    private fun finishAll(mc: Minecraft) {
        stopDelayTicks = STOP_GRACE_TICKS
        val frames = results.sumOf { it.frames }
        val checks = results.sumOf { it.checks }
        val failed = results.sumOf { it.failedChecks }
        log("ALL SCENES DONE: scenes=${results.size} frames=$frames checks=$checks failed=$failed")
        runCatching {
            outRoot.mkdirs()
            File(outRoot, "summary.tsv").writeText(
                buildString {
                    appendLine("scene\tframes\tdropped\tchecks\tfailedChecks")
                    results.forEach { appendLine("${it.name}\t${it.frames}\t${it.dropped}\t${it.checks}\t${it.failedChecks}") }
                    appendLine("TOTAL\t$frames\t${results.sumOf { it.dropped }}\t$checks\t$failed")
                },
            )
        }
        mc.stop()
    }

    private fun writeSceneFiles(current: Scene, failed: Int) {
        sceneLog.appendLine("frames\t$frameIndex")
        sceneLog.appendLine("framesDropped\t$framesDropped")
        sceneLog.appendLine("checks\t${checkLog.size}")
        sceneLog.appendLine("checksFailed\t$failed")
        checkLog.forEach { sceneLog.appendLine("check\t${it.render()}") }
        inServer(current.name, "write-meta") {
            File(current.dir, "meta.tsv").writeText(sceneLog.toString())
            File(current.dir, "frames.tsv").writeText(frameLines.toString())
        }
    }

    private fun ctx(server: MinecraftServer): SceneCtx =
        SceneCtx(server, server.overworld(), server.playerList.players.first(), this)

    private inline fun inServer(name: String, what: String, body: () -> Unit) {
        runCatching(body).onFailure { log("scene $name $what FAILED: $it") }
    }

    // ---------------------------------------------------------------- 抓帧（渲染线程）

    private fun onRenderFrame() {
        if (!captureArmed) return
        val want = pendingCaptureTick
        if (want < 0 || want == lastCapturedTick) return
        lastCapturedTick = want
        val current = scene ?: return
        if (inflightWrites.get() >= MAX_INFLIGHT_WRITES) {
            framesDropped++
            return
        }
        val mc = Minecraft.getInstance()
        val file = File(current.framesDir, String.format("%05d.png", frameIndex))
        val index = frameIndex
        try {
            Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), 1) { image ->
                inflightWrites.incrementAndGet()
                Util.ioPool().execute {
                    try {
                        image.writeToFile(file)
                    } catch (t: Throwable) {
                        log("write frame $index FAILED: $t")
                    } finally {
                        image.close()
                        inflightWrites.decrementAndGet()
                    }
                }
            }
        } catch (t: Throwable) {
            log("screenshot FAILED at tick $want: $t")
            return
        }
        frameLines.appendLine("$index\t$want\t${mc.level?.gameTime ?: -1L}")
        frameIndex++
    }

    // ---------------------------------------------------------------- 工具

    internal fun log(message: String) {
        LOG.info("[Capture] $message")
    }

    /** 单个场景的录制结果（写进 summary.tsv）。 */
    private class SceneResult(
        val name: String,
        val frames: Int,
        val dropped: Int,
        val checks: Int,
        val failedChecks: Int,
    )

    /** 场景上下文：服务端线程上的世界/玩家 + 几个高频动作。 */
    class SceneCtx(
        val server: MinecraftServer,
        val level: ServerLevel,
        val player: ServerPlayer,
        private val driver: CaptureDriver,
    ) {
        /** 执行一条原版命令（静默输出）。 */
        fun command(cmd: String) {
            server.commands.performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd)
        }

        /** 记一行带 `[Capture]` 前缀的日志。 */
        fun log(message: String) = driver.log(message)

        /**
         * 把玩家瞬移到施法点，**不发任何包**（`setPos` 只改服务端权威位置）。
         *
         * 为什么不发：静止投射物的出生点 = 施法者眼睛位置，而施法者同时又充当摄像机。
         * 若这里也发一次传送包，客户端会在同一 tick 收到「去球心」+「回机位」两个包，
         * 万一分属两个客户端 tick 就会闪一帧球心视角。改位置不发包，本 tick 稍后的
         * 机位传送包就是唯一的权威位置，客户端只会看到一次移动。
         */
        fun placeForCast(x: Double, y: Double, z: Double) {
            player.setPos(x, y, z)
            player.setOldPosAndRot()
        }

        /** 某列第一个非空气方块的下沿 y（= 脚踩的方块）；超平坦世界里就是草方块那一层。 */
        fun floorBlockY(x: Int, z: Int): Int =
            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1

        /**
         * 黑底摄影棚：地面换成黑色混凝土、四周立一圈墙，让粒子（尤其是细线）有对比度。
         *
         * 这是**为了看清特效**布置的取景环境，不是玩法场景；想录自然地形把这个调用删掉即可。
         */
        fun buildStudio(
            cx: Int,
            cz: Int,
            floorY: Int,
            half: Int = 16,
            wallHeight: Int = 28,
            color: DyeColor = DyeColor.BLACK,
        ) {
            // 默认黑底：让粒子（尤其细线）有对比度。**内爆场景改用中灰** —— 那一段的核心是黑色球体，
            // 黑底里看不见（见 CaptureScenes.implosion）。
            val state = Blocks.CONCRETE.pick(color).defaultBlockState()
            for (x in cx - half..cx + half) {
                for (z in cz - half..cz + half) {
                    level.setBlock(BlockPos(x, floorY, z), state, BLOCK_FLAG)
                }
            }
            for (y in floorY + 1..floorY + wallHeight) {
                // 墙要够高：否则 16:9 画面上沿会露出亮蓝天空，抢走粒子对比度
                for (i in -half..half) {
                    level.setBlock(BlockPos(cx + i, y, cz - half), state, BLOCK_FLAG)
                    level.setBlock(BlockPos(cx + i, y, cz + half), state, BLOCK_FLAG)
                    level.setBlock(BlockPos(cx - half, y, cz + i), state, BLOCK_FLAG)
                    level.setBlock(BlockPos(cx + half, y, cz + i), state, BLOCK_FLAG)
                }
            }
            driver.log("studio built at ($cx,$cz) floor=$floorY half=$half wall=$wallHeight")
        }

        /**
         * 生成一个测试木桩当「护盾持有者」。
         *
         * 为什么要替身：能量盾的球壳锚在**持有者**身上，若用玩家自己当持有者，
         * 摄像机就只能待在球壳内部（第三人称默认距离 4 格，而球壳直径 3.4 格）——
         * 第 16 轮那份录出来就是「一屏蓝色方块，看不出是球」。换成木桩后摄像机可以退到 7 格外，
         * 第一人称把整颗球壳收进画面，还能绕圈看五/六边形。
         */
        fun spawnDummy(x: Double, y: Double, z: Double, yaw: Float = 0f): TestDummyEntity {
            val dummy = TestDummyEntity(ModEntities.TEST_DUMMY.get(), level)
            dummy.setPos(x, y, z)
            dummy.setYRot(yaw)
            dummy.setNoGravity(true)
            dummy.setInvulnerable(true)
            level.addFreshEntity(dummy)
            return dummy
        }

        /**
         * 场景公共准备：清上一场景残留 + 冻结时间/天气 + 禁怪 + 设游戏模式 + 传送。
         *
         * 残留清理用「把投射物标记为死亡」而不是直接删：`MagicManager.tick` 会替我们调 `onDie`，
         * 于是退场动画照常播完（这正是在测的路径）。
         */
        fun setup(spectator: Boolean, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
            MagicManager.projectiles.forEach { it.alive = false }
            command("gamerule advance_time false")
            command("gamerule advance_weather false")
            command("gamerule spawn_mobs false")
            command("gamerule random_tick_speed 0")
            command("time set noon")
            command("weather clear")
            command("difficulty peaceful")
            command("gamemode ${if (spectator) "spectator" else "creative"}")
            player.connection.teleport(x, y, z, yaw, pitch)
            player.setNoGravity(true)
        }

        companion object {
            /** `Block.UPDATE_CLIENTS`：只改方块并发给客户端，不触发邻接更新（布景够用且快）。 */
            private const val BLOCK_FLAG = 2
        }
    }
}
