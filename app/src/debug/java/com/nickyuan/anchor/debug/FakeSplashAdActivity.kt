package com.nickyuan.anchor.debug

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 模拟开屏广告页（仅 debug 包）。
 *
 * 尽量模仿真实开屏广告：全屏画面、右上角"跳过 N"倒计时按钮、底部"摇一摇"提示，
 * 并且真的监听加速度传感器——摇一下就跳到广告详情页。
 * M2 的无障碍服务要做到：在用户摇到之前，自动点掉"跳过"。
 *
 * 实现 SensorEventListener 接口：传感器有新数据时，系统会回调 onSensorChanged。
 */
class FakeSplashAdActivity : ComponentActivity(), SensorEventListener {

    // lateinit：声明时先不赋值，保证在 onCreate 里赋值后再使用（类似 Java 里先声明后在 init 方法中赋值）
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    /** 广告出现的时刻，用来计算"多久之后被跳过 / 被摇到" */
    private var shownAtMs = 0L

    /** 是否已经有结果了。传感器事件很密集，防止同一次晃动触发多次跳转 */
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        shownAtMs = SystemClock.elapsedRealtime()
        // getSystemService：向系统要一个"系统服务"对象，这里是传感器管理器
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 拦截返回键：记录结果后再关闭页面
        onBackPressedDispatcher.addCallback(this) {
            complete(FakeAdResult.BackPressed(elapsedMs()))
            finish()
        }

        setContent {
            FakeSplashAdScreen(
                countdownSeconds = COUNTDOWN_SECONDS,
                onSkip = {
                    complete(FakeAdResult.Skipped(elapsedMs()))
                    goToAnchorHome()
                },
                onCountdownFinished = {
                    complete(FakeAdResult.CountdownFinished())
                    goToAnchorHome()
                },
            )
        }
    }

    /**
     * 生命周期回调：页面来到前台、用户能看到时调用。在这里开始监听传感器。
     * SENSOR_DELAY_GAME：采样频率约 50 次/秒，足够捕捉晃动。
     */
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /** 生命周期回调：页面离开前台时调用。必须停止监听，否则会持续耗电。 */
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    /** 传感器有新数据时调用（在主线程）。values[0..2] 分别是 x、y、z 方向的加速度 */
    override fun onSensorChanged(event: SensorEvent) {
        if (completed) return
        val (x, y, z) = event.values
        if (ShakeDetector.isShake(x, y, z)) {
            complete(FakeAdResult.Shaken(elapsedMs()))
            startActivity(Intent(this, FakeAdLandingActivity::class.java))
            finish()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit // 不关心精度变化

    /** 记录结果。只记录第一次，之后的点击、晃动、倒计时都忽略 */
    private fun complete(result: FakeAdResult) {
        if (completed) return
        completed = true
        FakeAdResultStore.record(result)
    }

    private fun elapsedMs(): Long = SystemClock.elapsedRealtime() - shownAtMs

    private companion object {
        const val COUNTDOWN_SECONDS = 5
    }
}

/** 广告页界面 */
@Composable
private fun FakeSplashAdScreen(
    countdownSeconds: Int,
    onSkip: () -> Unit,
    onCountdownFinished: () -> Unit,
) {
    // remember + mutableIntStateOf：一个"会触发界面刷新的变量"，值变了，用到它的文字会自动更新
    var remaining by remember { mutableIntStateOf(countdownSeconds) }
    // rememberUpdatedState：保证下面的协程里总是调用最新传进来的回调
    val currentOnFinished by rememberUpdatedState(onCountdownFinished)

    // LaunchedEffect：页面显示时启动一个协程（轻量级线程）。页面关闭时协程会自动取消
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000) // 挂起 1 秒，不会卡住界面
            remaining--
        }
        currentOnFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFF7A45), Color(0xFFD4380D)))),
    ) {
        // 左上角"广告"标签
        Text(
            text = "广告",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding() // 避开顶部状态栏
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        // 右上角"跳过 N"按钮：半透明黑底小圆角，和大多数真实开屏广告一样
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onSkip)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(text = "跳过 $remaining", color = Color.White, fontSize = 14.sp)
        }

        // 中间：广告主体
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("超值优惠 限时抢购", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("模拟开屏广告 · 仅用于测试", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        }

        // 底部："摇一摇"提示
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding() // 避开底部导航栏
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📳", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text("摇一摇 查看详情", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("或点击跳转第三方应用", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
        }
    }
}

@Preview
@Composable
private fun FakeSplashAdScreenPreview() {
    FakeSplashAdScreen(countdownSeconds = 5, onSkip = {}, onCountdownFinished = {})
}
