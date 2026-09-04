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

    override fun onInit() = configs { isDebug = true }

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

            DexKitBridge.create(appInfo.sourceDir).use { bridge ->
                val baseClassData = bridge.getClassData("com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel") ?: run {
                    loggerD(msg = "找不到 BaseListFragmentPanel")
                    return@loadApp
                }

                val handleDoubleClickData = bridge.findMethod {
                    searchClasses = listOf(baseClassData)
                    matcher {
                        name = "handleDoubleClick"
                        params { add("android.view.MotionEvent") }
                    }
                }.singleOrNull()

                val handleVideoEventData = bridge.findMethod {
                    searchClasses = listOf(baseClassData)
                    matcher {
                        name = "handleVideoEvent"
                        paramCount = 1
                        returnType = "void"
                    }
                }.singleOrNull()

                val getCurrentAwemeData = bridge.findMethod {
                    searchClasses = listOf(baseClassData)
                    matcher {
                        name = "getCurrentAweme"
                        paramCount = 0
                    }
                }.singleOrNull()

                if (handleDoubleClickData == null || handleVideoEventData == null || getCurrentAwemeData == null) {
                    loggerD(msg = "BaseListFragmentPanel 方法定位失败: doubleClick=$handleDoubleClickData, videoEvent=$handleVideoEventData, currentAweme=$getCurrentAwemeData")
                    return@loadApp
                }

                loggerD(msg = "BaseListFragmentPanel = ${baseClassData.name}")
                loggerD(msg = "handleDoubleClick = ${handleDoubleClickData.methodName}(${handleDoubleClickData.paramTypeNames.joinToString()})")
                loggerD(msg = "handleVideoEvent = ${handleVideoEventData.methodName}(${handleVideoEventData.paramTypeNames.joinToString()})")
                loggerD(msg = "getCurrentAweme = ${getCurrentAwemeData.methodName}()")

                val videoEventClassData = bridge.findClass {
                    matcher {
                        usingStrings {
                            add(StringMatcher("VideoEvent", StringMatchType.Contains))
                            add(StringMatcher("param", StringMatchType.Contains))
                            add(StringMatcher("videoType", StringMatchType.Contains))
                            add(StringMatcher("isPlaying", StringMatchType.Contains))
                        }
                        methods { add { name = "toString" } }
                    }
                }.singleOrNull() ?: run {
                    loggerD(msg = "找不到 VideoEvent 类")
                    return@loadApp
                }

                loggerD(msg = "VideoEvent = ${videoEventClassData.name}")

                val baseClass = Class.forName(baseClassData.name, false, classLoader)
                val videoEventClass = Class.forName(videoEventClassData.name, false, classLoader)

                val videoEventConstructor = videoEventClass.declaredConstructors.firstOrNull { constructor ->
                    val types = constructor.parameterTypes
                    types.size == 2 && 
                    (types[0] == Int::class.javaPrimitiveType || types[0] == Int::class.javaObjectType) &&
                    types[1].isAssignableFrom(Class.forName("com.ss.android.ugc.aweme.feed.model.Aweme", false, classLoader))
                } ?: run {
                    loggerD(msg = "找不到 VideoEvent(int, Aweme) 构造函数")
                    videoEventClass.declaredConstructors.forEach { loggerD(msg = "VideoEvent 构造函数: $it") }
                    return@loadApp
                }

                videoEventConstructor.isAccessible = true
                loggerD(msg = "VideoEvent 构造函数 = $videoEventConstructor")

                findClass(baseClass.name).hook {
                    injectMember {
                        method {
                            name = handleDoubleClickData.methodName
                            param(*handleDoubleClickData.paramTypeNames.toTypedArray())
                        }
                        beforeHook {
                            try {
                                val getCurrentAwemeMethod = baseClass.getDeclaredMethod(getCurrentAwemeData.methodName).apply { isAccessible = true }
                                val aweme = getCurrentAwemeMethod.invoke(instance) ?: run {
                                    loggerD(msg = "getCurrentAweme() 返回 null")
                                    return@beforeHook
                                }

                                loggerD(msg = "当前 Aweme = ${aweme.javaClass.name}")
                                val openCommentEvent = videoEventConstructor.newInstance(7, aweme)
                                loggerD(msg = "VideoEvent 创建成功")

                                val handleVideoEventMethod = baseClass.declaredMethods.firstOrNull { method ->
                                    method.name == handleVideoEventData.methodName && method.parameterTypes.size == handleVideoEventData.paramTypeNames.size
                                } ?: run {
                                    loggerD(msg = "找不到 handleVideoEvent 实例方法")
                                    return@beforeHook
                                }

                                handleVideoEventMethod.apply { isAccessible = true }.invoke(instance, openCommentEvent)
                                loggerD(msg = "成功打开评论区")
                                resultNull()
                            } catch (e: Throwable) {
                                loggerD(msg = "双击打开评论区失败: ${e.stackTraceToString()}")
                            }
                        }
                    }
                }

                val onVideoPlayerEventData = bridge.findMethod {
                    searchClasses = listOf(baseClassData)
                    matcher {
                        name = "onVideoPlayerEvent"
                        paramCount = 1
                        returnType = "void"
                    }
                }.singleOrNull()

                val pauseMethodData = bridge.findMethod {
                    searchClasses = listOf(baseClassData)
                    matcher {
                        name = "pauseCurrentPlayerWithListener"
                        paramCount = 0
                        returnType = "void"
                    }
                }.singleOrNull()

                val showPauseMethodData = bridge.findMethod {
                    searchClasses = listOf(baseClassData)
                    matcher {
                        name = "showIvWhenPause"
                        paramCount = 0
                        returnType = "void"
                    }
                }.singleOrNull()

                if (onVideoPlayerEventData != null && pauseMethodData != null) {
                    findClass(baseClass.name).hook {
                        injectMember {
                            method {
                                name = onVideoPlayerEventData.methodName
                                param(*onVideoPlayerEventData.paramTypeNames.toTypedArray())
                            }
                            afterHook {
                                try {
                                    val event = args[0] ?: return@afterHook
                                    val codeField = event.javaClass.declaredFields.firstOrNull { it.type == Int::class.javaPrimitiveType } ?: return@afterHook
                                    codeField.isAccessible = true
                                    if (codeField.getInt(event) != 7) return@afterHook

                                    baseClass.getDeclaredMethod(pauseMethodData.methodName).apply { isAccessible = true }.invoke(instance)
                                    showPauseMethodData?.let {
                                        baseClass.getDeclaredMethod(it.methodName).apply { isAccessible = true }.invoke(instance)
                                    }
                                    loggerD(msg = "视频播放完成，自动暂停")
                                } catch (e: Throwable) {
                                    loggerD(msg = "自动停止播放失败 ${e.stackTraceToString()}")
                                }
                            }
                        }
                    }
                }

                loggerD(msg = "DouyinHelp 双击评论 Hook 安装成功")
            }
        }
    }
}
