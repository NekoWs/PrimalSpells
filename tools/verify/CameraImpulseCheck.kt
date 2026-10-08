import work.nekow.primalspells.client.CameraImpulse
import kotlin.math.*
fun main() {
    var tests = 0
    for (duration in 4..16) {
        fun value(t: Double) = CameraImpulse.sample(t, duration.toDouble())
        check(value(0.0) == (0.0 to 0.0)); check(value(duration.toDouble()) == (0.0 to 0.0)); tests += 2
        val epsilon = 1e-5
        check(abs(value(epsilon).first / epsilon) < 1e-3)
        check(abs(value(epsilon).second / epsilon) < 1e-3)
        check(abs(value(duration-epsilon).second / epsilon) < 1e-3); tests += 3
        var prev = value(0.0)
        for (i in 1..duration*1000) {
            val next = value(i/1000.0)
            check(abs(next.first-prev.first) < .002 && abs(next.second-prev.second) < .002)
            check(abs(tanh(next.first)*.45) < .45 && abs(tanh(next.second)*.65) < .65)
            prev = next; tests += 2
        }
        // 新冲击出现时为零，重叠不会重置已经进行中的曲线。
        val before = value(duration*.4)
        val added = CameraImpulse.sample(0.0, 8.0)
        check(before == (before.first+added.first to before.second+added.second)); tests++
    }
    println("PASS $tests camera continuity / endpoints / bounds / overlap checks")
}
