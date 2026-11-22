package com.wlgh.hgtv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wlgh.hgtv.ui.HgTheme

/**
 * 原生入口。手机与 TV 的界面分别在 flavor source set 的 AppRoot() 中实现：
 *  - src/phone -> 手机端 Material3 界面
 *  - src/tv    -> 电视端大屏/遥控器界面
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            HgTheme {
                AppRoot()
            }
        }
    }
}
