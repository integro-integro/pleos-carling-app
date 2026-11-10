package work.integro.carling.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 카링을 진짜 젤리처럼 출렁이게 하는 말랑 물리.
 *
 * 그림 위에 4×4 격자 점을 두고, 점마다 제자리로 돌아가려는 용수철과 이웃 점을 끌어당기는 힘을 준다.
 * 몸이 움직이면(걷기·뛰기·끌기·착지) 관성 때문에 위쪽 점들이 늦게 따라와 출렁이고,
 * 발 쪽 점은 단단해서 땅에 붙어 있다. 누르면 그 자리가 콕 들어갔다가 튕겨 나온다.
 * 위가 눌리면 옆으로 퍼져 부피가 그대로인 것처럼 보인다.
 *
 * 그림 자체는 바꾸지 않고, 그리는 순간에 셰이더로 픽셀 위치만 비튼다 (Android 13 이상).
 * 그보다 낮은 기기에서는 기존의 찌그러짐 연출만 쓴다.
 */
class JellyBody {
    private val dx = FloatArray(N * N)
    private val dy = FloatArray(N * N)
    private val vx = FloatArray(N * N)
    private val vy = FloatArray(N * N)
    private var time = 0f

    /** 움직임이 거의 없으면 매 프레임 다시 그릴 필요가 없다. */
    var settled = true
        private set

    /**
     * [ax], [ay]: 몸 전체의 가속도 (그림 한 변 = 1 단위, 초당²).
     * 몸이 오른쪽으로 가속하면 젤리 윗부분은 왼쪽으로 처진다.
     */
    fun step(dt: Float, ax: Float, ay: Float) {
        val h = dt.coerceIn(0f, 1f / 30f)
        time += h
        // 한 프레임을 잘게 나눠 계산해 단단한 발 쪽 용수철도 안정적으로 움직이게 한다
        val sub = 3
        val s = h / sub
        var energy = 0f
        repeat(sub) {
            for (j in 0 until N) {
                val k = STIFF[j]
                val c = DAMP[j]
                val inertia = INERTIA[j]
                for (i in 0 until N) {
                    val n = j * N + i
                    // 이웃 점과 모양을 맞추려는 힘 (출렁임이 몸 전체로 번진다)
                    var lx = 0f
                    var ly = 0f
                    var cnt = 0
                    if (i > 0) { lx += dx[n - 1]; ly += dy[n - 1]; cnt++ }
                    if (i < N - 1) { lx += dx[n + 1]; ly += dy[n + 1]; cnt++ }
                    if (j > 0) { lx += dx[n - N]; ly += dy[n - N]; cnt++ }
                    if (j < N - 1) { lx += dx[n + N]; ly += dy[n + N]; cnt++ }
                    lx = lx / cnt - dx[n]
                    ly = ly / cnt - dy[n]
                    val fx = -k * dx[n] - c * vx[n] - ax * inertia + COUPLE * lx
                    val fy = -k * dy[n] - c * vy[n] - ay * inertia + COUPLE * ly
                    vx[n] += fx * s
                    vy[n] += fy * s
                }
            }
            for (n in 0 until N * N) {
                dx[n] = (dx[n] + vx[n] * s).coerceIn(-MAX_D, MAX_D)
                dy[n] = (dy[n] + vy[n] * s).coerceIn(-MAX_D, MAX_D)
            }
        }
        for (n in 0 until N * N) energy += abs(dx[n]) + abs(vx[n]) * 0.05f + abs(dy[n]) + abs(vy[n]) * 0.05f
        settled = energy < 0.002f
    }

    /** 누른 자리([u],[v]는 그림 안 0~1)가 안쪽으로 콕 들어가고, 몸 전체가 살짝 출렁인다. */
    fun poke(u: Float, v: Float, strength: Float = 2.8f) {
        for (j in 0 until N) for (i in 0 until N) {
            val n = j * N + i
            val px = i / (N - 1f)
            val py = j / (N - 1f)
            val ddx = px - u
            val ddy = py - v
            val d2 = ddx * ddx + ddy * ddy
            val w = exp(-d2 / 0.08f) * (1f - INERTIA_FLOOR * (j / (N - 1f)))
            // 누른 곳에서 멀어지는 쪽이 아니라 몸 가운데 쪽으로 눌린다
            val cx = 0.5f - u
            val cy = 0.55f - v
            val len = sqrt(cx * cx + cy * cy).coerceAtLeast(0.15f)
            vx[n] += cx / len * strength * w * 0.6f
            vy[n] += (cy / len * 0.6f + 0.5f) * strength * w
        }
        settled = false
    }

    /** 바로 셰이더에 넘길 값: 점마다 (x, y) 이동량. 위가 눌리면 가운데 줄이 옆으로 불룩해진다. */
    fun displacements(breathing: Boolean): FloatArray {
        val out = FloatArray(N * N * 2)
        // 윗줄이 아래로 눌린 만큼(+) 옆으로 퍼진다
        var press = 0f
        for (i in 0 until N) press += dy[i]
        press /= N
        val breath = if (breathing) sin(time * 2.4f) * 0.008f else 0f
        for (j in 0 until N) for (i in 0 until N) {
            val n = j * N + i
            val u = i / (N - 1f) - 0.5f
            val rowW = BULGE[j]
            out[n * 2] = dx[n] + u * 2f * (press * 1.1f + breath * 0.6f) * rowW
            out[n * 2 + 1] = dy[n] + breath * (1f - j / (N - 1f))
        }
        return out
    }

    companion object {
        const val N = 4
        private const val MAX_D = 0.14f
        private const val COUPLE = 60f
        private const val INERTIA_FLOOR = 0.85f

        // 위(머리) → 아래(발). 위는 말랑하고 천천히, 발은 단단하게.
        private val STIFF = floatArrayOf(110f, 150f, 260f, 900f)
        private val DAMP = floatArrayOf(5.5f, 6.5f, 9f, 20f)
        private val INERTIA = floatArrayOf(0.32f, 0.24f, 0.13f, 0.03f)
        private val BULGE = floatArrayOf(0.35f, 1f, 0.8f, 0.2f)

        /** 격자 점들의 이동량을 부드럽게 이어 붙여 각 픽셀이 어디서 왔는지 찾는다. */
        const val SHADER = """
            uniform shader content;
            uniform float2 size;
            uniform float2 d[16];

            half4 main(float2 p) {
                float2 uv = p / size;
                float2 g = clamp(uv, 0.0, 1.0) * 3.0;
                float2 disp = float2(0.0);
                float wsum = 0.0;
                for (int k = 0; k < 16; k++) {
                    float2 node = float2(float(k - (k / 4) * 4), float(k / 4));
                    float2 t = max(1.0 - abs(g - node), 0.0);
                    t = t * t * (3.0 - 2.0 * t);
                    float w = t.x * t.y;
                    disp += d[k] * w;
                    wsum += w;
                }
                disp /= max(wsum, 0.0001);
                return content.eval(p - disp * size);
            }
        """
    }
}

/** 젤리 셰이더를 쓸 수 있는 기기인지 (Android 13 이상). */
val JellySupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class JellyShader {
    private val shader = RuntimeShader(JellyBody.SHADER)

    fun effect(width: Float, height: Float, disp: FloatArray): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("d", disp)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
