package com.nickyuan.anchor.debug

import android.app.Activity
import android.content.Intent
import com.nickyuan.anchor.MainActivity

/**
 * 从测试页回到 Anchor 主页，并关闭当前页面。
 *
 * Intent：Android 里"我要打开某个页面"的请求对象，startActivity(intent) 把请求交给系统。
 * 几个 flag 的作用：
 * - NEW_TASK：在 Anchor 主页所在的任务（可理解为"最近任务"里的一张卡片）中打开，
 *   而不是塞进测试页自己的任务里；
 * - CLEAR_TOP + SINGLE_TOP：如果主页已经开着，就复用它，不再新建一个。
 */
internal fun Activity.goToAnchorHome() {
    val intent = Intent(this, MainActivity::class.java).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP,
    )
    startActivity(intent)
    finish() // 关闭当前页面
}
