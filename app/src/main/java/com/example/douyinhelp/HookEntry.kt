package com.example.douyinhelp

import com.example.douyinhelp.hook.AutoStopHook
import com.example.douyinhelp.hook.DoubleClickCommentHook
import com.highcapable.yukihookapi.hook.YukiHookAPI.encase
import com.highcapable.yukihookapi.hook.factory.preset.AppRegister
import org.luckypray.dexkit.DexKitBridge


class HookEntry : AppRegister() {


    override fun onHook() = encase {


        loadApp(
            name = "com.ss.android.ugc.aweme"
        ) {


            val classLoader =
                appClassLoader
                    ?: return@loadApp



            val apkPath =
                appInfo.sourceDir



            // =====================================================
            // 初始化 DexKit
            // =====================================================

            val bridge =
                DexKitBridge.create(
                    apkPath
                )
                    ?: return@loadApp



            try {


                // =================================================
                // 双击打开评论
                // =================================================

                DoubleClickCommentHook.hook(
                    bridge,
                    classLoader
                )



                // =================================================
                // 视频播放完成自动停止
                // =================================================

                AutoStopHook.hook(
                    bridge,
                    classLoader
                )



            }
            catch (e:Throwable){


                e.printStackTrace()


            }
            finally {


                bridge.close()


            }

        }

    }

}
