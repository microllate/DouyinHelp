package com.example.douyinhelp

import android.content.Context
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import org.luckypray.dexkit.DexKitBridge

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = true
    }

    override fun onHook() = encase {
        loadApp(name = "com.ss.android.ugc.aweme") {
            val apkPath = appInfo.sourceDir
            System.loadLibrary("dexkit")

            DexKitBridge.create(apkPath).use { bridge ->
                // 1. 查找手势监听类（包含 onDoubleTap 字符串特征）
                val doubleTapClassName = bridge.findClass {
                    matcher {
                        usingString("onDoubleTap")
                    }
                }.firstOrNull()?.name

                // 2. 查找事件总线 EventBus 类（包含 post 方法与单例获取方法）
                val eventBusClassData = bridge.findClass {
                    matcher {
                        usingString("No subscribers registered for event")
                    }
                }.firstOrNull()

                // 3. 查找打开评论区的 Event 事件类特征
                val commentEventClassName = bridge.findClass {
                    matcher {
                        usingString("/douyin/comment")
                    }
                }.firstOrNull()?.name

                if (doubleTapClassName == null || eventBusClassData == null || commentEventClassName == null) {
                    loggerD(msg = "DexKit 匹配特征类失败，请检查抖音版本特征字符串！")
                    return@loadApp
                }

                val eventBusClass = eventBusClassData.getInstance(appClassLoader)
                val commentEventClass = findClass(commentEventClassName)

                // 4. Hook 双击手势
                findClass(doubleTapClassName).hook {
                    injectMember {
                        method {
                            name = "onDoubleTap"
                        }
                        beforeHook {
                            // 阻止默认的双击点赞动作
                            resultNull()

                            try {
                                // 提取手势回调中的 Aweme 对象或视频 ID (aid)
                                val awemeObj = args.firstOrNull { 
                                    it != null && it.javaClass.name.contains("Aweme") 
                                }

                                if (awemeObj != null) {
                                    // 实例化评论区事件对象 (传入当前 Aweme)
                                    val commentEvent = commentEventClass.getConstructor(awemeObj.javaClass)
                                        .newInstance(awemeObj)

                                    // 获取 EventBus 单例并 post 分发 open-comment-panel 事件
                                    val eventBusInstance = eventBusClass.method { name = "getDefault" }
                                        .get().invoke()
                                    
                                    eventBusClass.method { name = "post"; param(Any::class.java) }
                                        .get(eventBusInstance).invoke(commentEvent)

                                    loggerD(msg = "成功拦截双击，已发送 open-comment-panel 事件！")
                                }
                            } catch (e: Throwable) {
                                loggerD(msg = "发送评论区事件异常: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }
}
