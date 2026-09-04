package com.example.douyinhelp

import android.app.Application
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import de.robv.android.xposed.helpers.AndroidAppHelper
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.base.StringMatcher
import java.lang.reflect.Field

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs { isDebug = true }

    override fun onHook() = encase {
        loadApp(name = "com.ss.android.ugc.aweme") {
            try {
                System.loadLibrary("dexkit")
            } catch (e: Throwable) {
                loggerD(msg = "DexKit load failed: ${e.stackTraceToString()}")
                return@loadApp
            }

            val classLoader = appClassLoader ?: run {
                loggerD(msg = "ClassLoader is null")
                return@loadApp
            }

            val application: Application = AndroidAppHelper.currentApplication() ?: run {
                loggerD(msg = "Application is null")
                return@loadApp
            }
            DownloadHelper.registerClipboardListener(application)

            DexKitBridge.create(appInfo.sourceDir).use { bridge ->
                val baseClassData = bridge.getClassData("com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel") ?: run {
                    loggerD(msg = "BaseListFragmentPanel not found")
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
                    loggerD(msg = "Method search failed: doubleClick=$handleDoubleClickData, videoEvent=$handleVideoEventData, currentAweme=$getCurrentAwemeData")
                    return@loadApp
                }

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
                    loggerD(msg = "VideoEvent class not found")
                    return@loadApp
                }

                val baseClass = Class.forName(baseClassData.name, false, classLoader)
                val videoEventClass = Class.forName(videoEventClassData.name, false, classLoader)
                val awemeClass = Class.forName("com.ss.android.ugc.aweme.feed.model.Aweme", false, classLoader)

                val getCurrentAwemeMethod = baseClass.getDeclaredMethod(getCurrentAwemeData.methodName).apply { isAccessible = true }
                val handleVideoEventMethod = baseClass.declaredMethods.firstOrNull { method ->
                    method.name == handleVideoEventData.methodName && method.parameterTypes.size == handleVideoEventData.paramTypeNames.size
                }?.apply { isAccessible = true } ?: run {
                    loggerD(msg = "handleVideoEvent instance method not found")
                    return@loadApp
                }

                val videoEventConstructor = videoEventClass.declaredConstructors.firstOrNull { constructor ->
                    val types = constructor.parameterTypes
                    types.size == 2 &&
                        (types[0] == Int::class.javaPrimitiveType || types[0] == Int::class.javaObjectType) &&
                        types[1].isAssignableFrom(awemeClass)
                }?.apply { isAccessible = true } ?: run {
                    loggerD(msg = "VideoEvent constructor not found")
                    return@loadApp
                }

                findClass(baseClass.name).hook {
                    injectMember {
                        method {
                            name = handleDoubleClickData.methodName
                            param(*handleDoubleClickData.paramTypeNames.toTypedArray())
                        }
                        beforeHook {
                            try {
                                val aweme = getCurrentAwemeMethod.invoke(instance) ?: return@beforeHook
                                DownloadHelper.updateCurrentAweme(aweme)

                                val openCommentEvent = videoEventConstructor.newInstance(7, aweme)
                                handleVideoEventMethod.invoke(instance, openCommentEvent)
                                resultNull()
                            } catch (e: Throwable) {
                                loggerD(msg = "Double click action failed: ${e.stackTraceToString()}")
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
                    val pauseMethod = baseClass.getDeclaredMethod(pauseMethodData.methodName).apply { isAccessible = true }
                    val showPauseMethod = showPauseMethodData?.let { baseClass.getDeclaredMethod(it.methodName).apply { isAccessible = true } }

                    var cachedCodeField: Field? = null

                    findClass(baseClass.name).hook {
                        injectMember {
                            method {
                                name = onVideoPlayerEventData.methodName
                                param(*onVideoPlayerEventData.paramTypeNames.toTypedArray())
                            }
                            afterHook {
                                try {
                                    val aweme = getCurrentAwemeMethod.invoke(instance)
                                    DownloadHelper.updateCurrentAweme(aweme)

                                    val event = args[0] ?: return@afterHook
                                    val codeField = cachedCodeField ?: event.javaClass.declaredFields.firstOrNull {
                                        it.type == Int::class.javaPrimitiveType
                                    }?.apply {
                                        isAccessible = true
                                        cachedCodeField = this
                                    } ?: return@afterHook

                                    if (codeField.getInt(event) != 7) return@afterHook

                                    pauseMethod.invoke(instance)
                                    showPauseMethod?.invoke(instance)
                                } catch (e: Throwable) {
                                    loggerD(msg = "Video event handling failed: ${e.stackTraceToString()}")
                                }
                            }
                        }
                    }
                }

                loggerD(msg = "DouyinHelp hooks initialized successfully")
            }
        }
    }
}
