package com.example.douyinhelp

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Method

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = true
    }

    override fun onHook() = encase {
        loadApp(name = "com.ss.android.ugc.aweme") {

            val apkPath = appInfo.sourceDir
            System.loadLibrary("dexkit")

            val classLoader = appClassLoader ?: run {
                loggerD(msg = "获取 App ClassLoader 失败")
                return@loadApp
            }

            DexKitBridge.create(apkPath).use { bridge ->

                // 1. 定位 BaseListFragmentPanel 类
                val panelClassName = bridge.findClass {
                    matcher {
                        usingStrings("BaseListFragmentPanel", "getCurrentAweme")
                    }
                }.firstOrNull()?.name ?: bridge.findClass {
                    matcher {
                        usingStrings("getCurrentAweme")
                    }
                }.firstOrNull()?.name

                // 2. 定位 VideoEvent 事件类
                val videoEventClassName = bridge.findClass {
                    matcher {
                        usingStrings("EVENT_OPEN_COMMENT_PANEL", "VideoEvent")
                    }
                }.firstOrNull()?.name ?: bridge.findClass {
                    matcher {
                        usingStrings("com/ss/android/ugc/aweme/feed/model/VideoEvent")
                    }
                }.firstOrNull()?.name

                if (panelClassName == null || videoEventClassName == null) {
                    loggerD(msg = "DexKit 定位 Panel 或 VideoEvent 失败")
                    return@loadApp
                }

                loggerD(msg = "Panel 类: $panelClassName")
                loggerD(msg = "VideoEvent 类: $videoEventClassName")

                val panelJavaClass = Class.forName(panelClassName, false, classLoader)
                val videoEventJavaClass = Class.forName(videoEventClassName, false, classLoader)

                // 3. 寻找 Panel 中无参且返回 Void 的双击处理方法 handleDoubleClick
                val handleDoubleClickMethod: Method = panelJavaClass.declaredMethods.firstOrNull { m ->
                    m.returnType == Void.TYPE && m.parameterTypes.isEmpty()
                } ?: run {
                    loggerD(msg = "未找到 handleDoubleClick 方法")
                    return@loadApp
                }

                // 4. 寻找 getCurrentAweme 方法
                val getCurrentAwemeMethod: Method = panelJavaClass.declaredMethods.firstOrNull { m ->
                    m.parameterTypes.isEmpty() && m.returnType.name.contains("Aweme")
                } ?: run {
                    loggerD(msg = "未找到 getCurrentAweme 方法")
                    return@loadApp
                }

                // 5. Hook 双击处理逻辑
                findClass(panelClassName).hook {
                    injectMember {
                        method {
                            name = handleDoubleClickMethod.name
                        }

                        beforeHook {
                            // 拦截原生双击点赞
                            resultNull()

                            try {
                                // 获取当前 Aweme
                                getCurrentAwemeMethod.isAccessible = true
                                val awemeObj = getCurrentAwemeMethod.invoke(instance) ?: run {
                                    loggerD(msg = "getCurrentAweme 返回为空")
                                    return@beforeHook
                                }

                                // 构造 VideoEvent(14, awemeObj) —— 14 通常代表 EVENT_OPEN_COMMENT_PANEL
                                val eventConstructor: Constructor<*>? = videoEventJavaClass.declaredConstructors.firstOrNull { c ->
                                    val params = c.parameterTypes
                                    params.size == 2 && 
                                    (params[0] == Int::class.javaPrimitiveType || params[0] == Integer::class.java) &&
                                    params[1].isAssignableFrom(awemeObj.javaClass)
                                }

                                if (eventConstructor == null) {
                                    loggerD(msg = "未找到合适的 VideoEvent 构造函数")
                                    return@beforeHook
                                }

                                eventConstructor.isAccessible = true
                                val openCommentEvent = eventConstructor.newInstance(14, awemeObj)

                                // 查找 Panel 中接收 VideoEvent 参数的分发方法 handleVideoEvent
                                val handleVideoEventMethod: Method? = panelJavaClass.declaredMethods.firstOrNull { m ->
                                    m.parameterTypes.size == 1 && m.parameterTypes[0].isAssignableFrom(videoEventJavaClass)
                                }

                                if (handleVideoEventMethod == null) {
                                    loggerD(msg = "未找到 handleVideoEvent 方法")
                                    return@beforeHook
                                }

                                handleVideoEventMethod.isAccessible = true
                                handleVideoEventMethod.invoke(instance, openCommentEvent)

                                loggerD(msg = "成功分发打开评论区事件")

                            } catch (e: Throwable) {
                                loggerD(msg = "触发打开评论区异常: ${e.stackTraceToString()}")
                            }
                        }
                    }
                }
            }
        }
    }
}
