package com.example.douyinhelp

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.base.StringMatcher

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = true
    }

    override fun onHook() = encase {
        loadApp(name = "com.ss.android.ugc.aweme") {

            try {
                System.loadLibrary("dexkit")
            } catch (e: Throwable) {
                loggerD(msg = "DexKit 加载失败: ${e.stackTraceToString()}")
                return@loadApp
            }

            val classLoader = appClassLoader ?: run {
                loggerD(msg = "获取抖音 ClassLoader 失败")
                return@loadApp
            }

            val apkPath = appInfo.sourceDir

            DexKitBridge.create(apkPath).use { bridge ->

                // ============================================================
                // 1. BaseListFragmentPanel
                // ============================================================

                val baseClassData =
                    bridge.getClassData(
                        "com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel"
                    )

                if (baseClassData == null) {
                    loggerD(msg = "找不到 BaseListFragmentPanel")
                    return@loadApp
                }

                // handleDoubleClick(MotionEvent)
                val handleDoubleClickData = bridge.findMethod {
                    searchClasses = listOf(baseClassData)

                    matcher {
                        name = "handleDoubleClick"

                        params {
                            add("android.view.MotionEvent")
                        }
                    }
                }.singleOrNull()

                // handleVideoEvent(一个参数，void)
                val handleVideoEventData = bridge.findMethod {
                    searchClasses = listOf(baseClassData)

                    matcher {
                        name = "handleVideoEvent"
                        paramCount = 1
                        returnType = "void"
                    }
                }.singleOrNull()

                // getCurrentAweme()
                val getCurrentAwemeData = bridge.findMethod {
                    searchClasses = listOf(baseClassData)

                    matcher {
                        name = "getCurrentAweme"
                        paramCount = 0
                    }
                }.singleOrNull()

                if (
                    handleDoubleClickData == null ||
                    handleVideoEventData == null ||
                    getCurrentAwemeData == null
                ) {
                    loggerD(msg = "BaseListFragmentPanel 方法定位失败")
                    loggerD(
                        msg = "doubleClick=$handleDoubleClickData, " +
                                "videoEvent=$handleVideoEventData, " +
                                "currentAweme=$getCurrentAwemeData"
                    )
                    return@loadApp
                }

                loggerD(
                    msg = "BaseListFragmentPanel = ${baseClassData.name}"
                )

                loggerD(
                    msg = "handleDoubleClick = " +
                            "${handleDoubleClickData.methodName}(" +
                            handleDoubleClickData.paramTypeNames.joinToString() +
                            ")"
                )

                loggerD(
                    msg = "handleVideoEvent = " +
                            "${handleVideoEventData.methodName}(" +
                            handleVideoEventData.paramTypeNames.joinToString() +
                            ")"
                )

                loggerD(
                    msg = "getCurrentAweme = " +
                            "${getCurrentAwemeData.methodName}()"
                )

                // ============================================================
                // 2. 找 VideoEvent
                // ============================================================

                val videoEventClassData = bridge.findClass {
                    matcher {
                        usingStrings {
                            add(StringMatcher("VideoEvent", StringMatchType.Contains))
                            add(StringMatcher("param", StringMatchType.Contains))
                            add(StringMatcher("videoType", StringMatchType.Contains))
                            add(StringMatcher("isPlaying", StringMatchType.Contains))
                        }

                        methods {
                            add {
                                name = "toString"
                            }
                        }
                    }
                }.singleOrNull()

                if (videoEventClassData == null) {
                    loggerD(msg = "找不到 VideoEvent 类")
                    return@loadApp
                }

                loggerD(
                    msg = "VideoEvent = ${videoEventClassData.name}"
                )

                // ============================================================
                // 3. 获取真正的 Java Class
                // ============================================================

                val baseClass =
                    Class.forName(
                        baseClassData.name,
                        false,
                        classLoader
                    )

                val videoEventClass =
                    Class.forName(
                        videoEventClassData.name,
                        false,
                        classLoader
                    )

                // ============================================================
                // 4. 找 VideoEvent(int, Aweme) 构造函数
                // ============================================================

                val videoEventConstructor =
                    videoEventClass.declaredConstructors
                        .firstOrNull { constructor ->

                            val types = constructor.parameterTypes

                            types.size == 2 &&
                                    (
                                            types[0] == Int::class.javaPrimitiveType ||
                                                    types[0] == Int::class.javaObjectType
                                            ) &&
                                    types[1].isAssignableFrom(
                                        Class.forName(
                                            "com.ss.android.ugc.aweme.feed.model.Aweme",
                                            false,
                                            classLoader
                                        )
                                    )
                        }

                if (videoEventConstructor == null) {
                    loggerD(
                        msg = "找不到 VideoEvent(int, Aweme) 构造函数"
                    )

                    videoEventClass.declaredConstructors.forEach {
                        loggerD(
                            msg = "VideoEvent 构造函数: $it"
                        )
                    }

                    return@loadApp
                }

                videoEventConstructor.isAccessible = true

                loggerD(
                    msg = "VideoEvent 构造函数 = $videoEventConstructor"
                )

                // ============================================================
                // 5. Hook handleDoubleClick(MotionEvent)
                // ============================================================

                findClass(baseClass.name).hook {

                    injectMember {

                        method {
                            name = handleDoubleClickData.methodName
                            param(*handleDoubleClickData.paramTypeNames.toTypedArray())
                        }

                        beforeHook {

                            try {

                                // ------------------------------------------------
                                // 获取当前 Aweme
                                // ------------------------------------------------

                                val getCurrentAwemeMethod =
                                    baseClass.getDeclaredMethod(
                                        getCurrentAwemeData.methodName
                                    ).apply {
                                        isAccessible = true
                                    }

                                val aweme =
                                    getCurrentAwemeMethod.invoke(instance)

                                if (aweme == null) {
                                    loggerD(
                                        msg = "getCurrentAweme() 返回 null"
                                    )
                                    return@beforeHook
                                }

                                loggerD(
                                    msg = "当前 Aweme = ${aweme.javaClass.name}"
                                )

                                // ------------------------------------------------
                                // 创建 VideoEvent
                                // EVENT_OPEN_COMMENT_PANEL = 7
                                // ------------------------------------------------

                                val openCommentEvent =
                                    videoEventConstructor.newInstance(
                                        7,
                                        aweme
                                    )

                                loggerD(
                                    msg = "VideoEvent 创建成功"
                                )

                                // ------------------------------------------------
                                // 调用当前 BaseListFragmentPanel.handleVideoEvent()
                                // ------------------------------------------------

                                val handleVideoEventMethod =
                                    baseClass.declaredMethods
                                        .firstOrNull { method ->

                                            method.name ==
                                                    handleVideoEventData.methodName &&
                                                    method.parameterTypes.size ==
                                                    handleVideoEventData.paramTypeNames.size
                                        }

                                if (handleVideoEventMethod == null) {
                                    loggerD(
                                        msg = "找不到 handleVideoEvent 实例方法"
                                    )
                                    return@beforeHook
                                }

                                handleVideoEventMethod.isAccessible = true

                                handleVideoEventMethod.invoke(
                                    instance,
                                    openCommentEvent
                                )

                                loggerD(
                                    msg = "成功打开评论区"
                                )

                                // ------------------------------------------------
                                // 最关键：
                                // 阻止原来的双击点赞
                                // ------------------------------------------------

                                resultNull()

                            } catch (e: Throwable) {

                                loggerD(
                                    msg =
                                        "双击打开评论区失败: " +
                                                e.stackTraceToString()
                                )
                            }
                        }
                    }
                }

                loggerD(
                    msg = "DouyinHelp 双击评论 Hook 安装成功"
                )
            }
        }
    }
}
