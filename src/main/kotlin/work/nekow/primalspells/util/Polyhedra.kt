package work.nekow.primalspells.util

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2

/**
 * 球面多面体几何 —— 目前只有**截角二十面体**（足球 / 五-六边形球），供能量护盾的球壳取点。
 *
 * 与 [ParticleShapes] 的分工：那边是「球面上怎么撒点」（均匀采样），这边是「点该撒在什么拓扑上」
 * （顶点 / 棱 / 面板）。两者都只做几何，不含任何粒子或渲染语义。
 *
 * ## 构造（全部在单位球面上算，最后乘 [radius]）
 *
 * 1. 正二十面体：12 个顶点（`(0,±1,±φ)` 的三个循环置换）、30 条等长棱、20 个三角面；
 * 2. **截断**每个顶点：每条棱上距两端 1/3 处各取一点并投回球面 → 60 个顶点；
 * 3. **面板**：每个原顶点变成一个**五边形**（5 条邻接棱上的截断点，绕该顶点按角度排序）；
 *    每个原三角面变成一个**六边形**（`[p(a→b), p(b→a), p(b→c), p(c→b), p(c→a), p(a→c)]`，
 *    相邻两点必共享一条棱，故这个顺序天然是绕面一圈）；
 * 4. 每个面板的 [Panel.center] 取边界点的重心方向（正多边形下它**就是**该面的法线方向）。
 *
 * 结果：**32 个面板**（12 五边形 + 20 六边形）、**60 个顶点**、**90 条棱**，所有棱等长；
 * 每个顶点属于 3 个面板、每条棱属于 2 个面板 —— 因此「每个面板各自铺自己的一圈边界」时，
 * 顶点会被画 3 次、棱被画 2 次（重叠处更亮，正好当节点用）。
 *
 * 构造过程带 `require` 自检（棱数/面数/顶点度数不对就直接抛错），避免静默画出一个错误的球。
 * 数值自检脚本与结果见 `docs/issues/resolved.md` 第 15 轮。
 */
object Polyhedra {

    /**
     * 一块球面面板（正多边形）。
     *
     * @param center 面板中心方向（球面上、长度 = [radius]）
     * @param ring   边界点（球面上、长度 = [radius]；绕面一圈，首尾不重复）
     */
    class Panel(val center: Vec3, val ring: List<Vec3>)

    /**
     * 截角二十面体在半径 [radius] 的球面上的全部面板，**坐标以球心为原点**（调用方自己加球心坐标）。
     *
     * @param radius        外接球半径（方块）：面板中心与边界点都落在这个球面上
     * @param pointsPerEdge 每条棱上放几个点（含起点、不含终点，终点由下一条棱给出）：
     *                      1 = 只放顶点（边界是点阵），2 = 顶点 + 中点（边界接近实线）
     * @return 32 个面板：前 12 个是五边形，后 20 个是六边形
     */
    fun truncatedIcosahedron(radius: Double, pointsPerEdge: Int = 2): List<Panel> {
        require(radius > 0.0) { "半径必须为正：$radius" }
        require(pointsPerEdge >= 1) { "每条棱至少要放 1 个点：$pointsPerEdge" }

        val vertices = icosahedronVertices()
        val edges = icosahedronEdges(vertices)
        val faces = icosahedronFaces(edges, vertices.size)

        // 截断点：有序顶点对 (from → to) 唯一确定一个点（同一条棱的两个方向各得一个）
        val cut = HashMap<Long, Vec3>(60)
        fun cutAt(from: Int, to: Int): Vec3 = cut.getOrPut(directedKey(from, to)) {
            vertices[from].add(vertices[to].subtract(vertices[from]).scale(1.0 / 3.0)).normalize()
        }

        val neighbors = Array(vertices.size) { ArrayList<Int>() }
        for (edge in edges) {
            neighbors[edge[0]] += edge[1]
            neighbors[edge[1]] += edge[0]
        }
        for ((v, list) in neighbors.withIndex()) {
            require(list.size == 5) { "正二十面体每个顶点应邻接 5 个顶点，顶点 $v 实得 ${list.size}" }
        }

        val panels = ArrayList<Panel>(32)
        // 12 个五边形：截断一个顶点得到的面
        for (v in vertices.indices) {
            val ring = neighbors[v].map { cutAt(v, it) }.sortedAround(vertices[v])
            panels += panel(ring, radius, pointsPerEdge)
        }
        // 20 个六边形：截断一个三角面得到的面
        for (face in faces) {
            val (a, b, c) = face
            val ring = listOf(
                cutAt(a, b), cutAt(b, a),
                cutAt(b, c), cutAt(c, b),
                cutAt(c, a), cutAt(a, c),
            )
            panels += panel(ring, radius, pointsPerEdge)
        }
        require(panels.size == 32) { "应有 32 个面板，实得 ${panels.size}" }
        require(cut.size == 60) { "截角二十面体应有 60 个顶点，实得 ${cut.size}" }
        return panels
    }

    // ---------- 内部：正二十面体 ----------

    private const val PHI = 1.618033988749895 // (1 + √5) / 2

    private val SIGNS = doubleArrayOf(-1.0, 1.0)

    /** 正二十面体的 12 个顶点（单位球面）：`(0,±1,±φ)` 的三个循环置换。 */
    private fun icosahedronVertices(): List<Vec3> {
        val out = ArrayList<Vec3>(12)
        for (a in SIGNS) {
            for (b in SIGNS) {
                out += Vec3(0.0, a, b * PHI).normalize()
                out += Vec3(a, b * PHI, 0.0).normalize()
                out += Vec3(b * PHI, 0.0, a).normalize()
            }
        }
        require(out.size == 12) { "正二十面体应有 12 个顶点，实得 ${out.size}" }
        return out
    }

    /** 棱 = 距离等于「最短顶点距」的顶点对（正二十面体所有棱等长，故按 1% 容差判定）。 */
    private fun icosahedronEdges(vertices: List<Vec3>): List<IntArray> {
        var shortest = Double.MAX_VALUE
        for (i in vertices.indices) {
            for (j in i + 1 until vertices.size) {
                shortest = minOf(shortest, vertices[i].distanceTo(vertices[j]))
            }
        }
        val out = ArrayList<IntArray>(30)
        for (i in vertices.indices) {
            for (j in i + 1 until vertices.size) {
                if (vertices[i].distanceTo(vertices[j]) < shortest * 1.01) out += intArrayOf(i, j)
            }
        }
        require(out.size == 30) { "正二十面体应有 30 条棱，实得 ${out.size}" }
        return out
    }

    /** 三角面 = 三个两两相邻的顶点（正二十面体的图里除面以外没有三角形，故这样枚举恰好得到 20 个面）。 */
    private fun icosahedronFaces(edges: List<IntArray>, vertexCount: Int): List<IntArray> {
        val adjacency = HashSet<Long>(edges.size * 2)
        for (edge in edges) {
            adjacency += pairKey(edge[0], edge[1])
            adjacency += pairKey(edge[1], edge[0])
        }
        val faces = ArrayList<IntArray>(20)
        for (i in 0 until vertexCount) {
            for (j in i + 1 until vertexCount) {
                if (pairKey(i, j) !in adjacency) continue
                for (k in j + 1 until vertexCount) {
                    if (pairKey(j, k) in adjacency && pairKey(i, k) in adjacency) faces += intArrayOf(i, j, k)
                }
            }
        }
        require(faces.size == 20) { "正二十面体应有 20 个面，实得 ${faces.size}" }
        return faces
    }

    // ---------- 内部：面板 ----------

    /**
     * 把一个面板的顶点环展开成边界点：先统一**绕向**（保证从球心看出去是逆时针，方便下游按需要三角化/偏移），
     * 再在每条 [ring] 棱上取 [pointsPerEdge] 个点（含起点）；插值结果先归一化再乘 [radius]，
     * 保证点严格落在球面上（弦上的插值点会落到球内，必须重新投回球面）。
     */
    private fun panel(ring: List<Vec3>, radius: Double, pointsPerEdge: Int): Panel {
        val ordered = orientOutward(ring)
        val points = ArrayList<Vec3>(ordered.size * pointsPerEdge)
        for (i in ordered.indices) {
            val from = ordered[i]
            val to = ordered[(i + 1) % ordered.size]
            val step = to.subtract(from)
            for (k in 0 until pointsPerEdge) {
                points += from.add(step.scale(k.toDouble() / pointsPerEdge)).normalize().scale(radius)
            }
        }
        val center = points.reduce { acc, p -> acc.add(p) }.normalize().scale(radius)
        return Panel(center, points)
    }

    /** 让顶点环**朝向球外**一致：环法线（前三个点的叉积）与「球心指向面心」同向，否则反转。 */
    private fun orientOutward(ring: List<Vec3>): List<Vec3> {
        val center = ring.reduce { acc, p -> acc.add(p) }
        val normal = ring[1].subtract(ring[0]).cross(ring[2].subtract(ring[0]))
        return if (normal.dot(center) < 0.0) ring.reversed() else ring
    }

    /** 把一组方向按「绕 [axis] 的角度」排序（用于把顶点周围的截断点排成正确的多边形顺序）。 */
    private fun List<Vec3>.sortedAround(axis: Vec3): List<Vec3> {
        val n = axis.normalize()
        val seed = if (abs(n.x) < 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        val u = seed.subtract(n.scale(seed.dot(n))).normalize()
        val w = n.cross(u)
        return sortedBy { p ->
            val tangent = p.subtract(n.scale(p.dot(n)))
            atan2(tangent.dot(w), tangent.dot(u))
        }
    }

    /** 无向顶点对的键（用于邻接/棱的集合查询）。 */
    private fun pairKey(a: Int, b: Int): Long = (minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()

    /** **有向**顶点对的键（用于「距起点 1/3 处的截断点」这类与方向有关的数据）。 */
    private fun directedKey(from: Int, to: Int): Long = (from.toLong() shl 32) or to.toLong()
}
