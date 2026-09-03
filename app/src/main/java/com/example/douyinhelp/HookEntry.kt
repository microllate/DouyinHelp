package com.example.douyinhelp

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import org.luckypray.dexkit.DexKitBridge

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = true
    }

    override fun onHook() = encase {
        loadApp(name = "com.ss.android.ugc.aweme") {
            // 1. 获取抖音宿主的 APK 路径或通过 ClassLoader 初始化 DexKit
            val apkPath = appInfo.sourceDir
            System.loadLibrary("dexkit") // 加载 DexKit Native 库
            
            DexKitBridge.create(apkPath).use { bridge ->
                // 2. 动态查找包含手势监听或双击特征的类
                val targetClassName = bridge.findClass {
                    matcher {
                        usingString("onDoubleTap")
                    }
                }.firstOrNull()?.name

                // 3. 找到类后使用 YukiHookAPI 进行 Hook
                if (targetClassName != null) {
                    findClass(targetClassName).hook {
                        injectMember {
                            method {
                                name = "onDoubleTap"
                            }
                            beforeHook {
                                // 拦截默认点赞
                                resultNull()
                            }
                        }
                    }
                }
            }
        }
    }
}
