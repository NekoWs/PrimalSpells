package work.nekow.primalspells.client

import kotlin.math.PI
import kotlin.math.sin

/** 镜头重音的纯时间曲线，供客户端逐帧求值及端点/叠加回归验证。 */
object CameraImpulse {
    /** 返回偏航/俯仰乘数；两端数值及速度均为零，没有起始跳变。 */
    fun sample(age: Double, duration: Double): Pair<Double, Double> {
        if (duration <= 0 || age <= 0 || age >= duration) return 0.0 to 0.0
        val p = age / duration
        val envelope = sin(PI * p).let { it * it } * (1 - p)
        return sin(2 * PI * p) * envelope * 0.55 to sin(3 * PI * p) * envelope
    }
}
