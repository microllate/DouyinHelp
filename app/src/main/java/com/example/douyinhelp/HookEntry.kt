package com.example.douyinhelp

import com.example.douyinhelp.hook.DoubleClickCommentHook
import com.example.douyinhelp.hook.AutoStopHook
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import org.luckypray.dexkit.DexKitBridge


@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {


    override fun onInit() = configs {

        isDebug = true

    }


    override fun onHook() = encase {


        loadApp(
            name = "com.ss.android.ugc.aweme"
        ) {


            try {

                System.loadLibrary("dexkit")


            } catch (e: Throwable) {


                loggerD(
                    msg = "DexKit 加载失败: ${e.stackTraceToString()}"
                )

                return@loadApp

            }



            val classLoader =
                appClassLoader
                    ?: return@loadApp



            val apkPath =
                appInfo.sourceDir



            DexKitBridge.create(apkPath).use { bridge ->


                // 双击评论

                DoubleClickCommentHook.hook(
                    bridge,
                    classLoader
                )


                // 自动停止播放

                AutoStopHook.hook(
                    bridge,
                    classLoader
                )


            }

        }

    }

}
