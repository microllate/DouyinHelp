package com.example.douyinhelp

import android.app.Application
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.base.StringMatcher
import java.lang.reflect.Field

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs { isDebug = true }

    override fun onHook() = encase {
        loadApp(name = "com.ss.android.ugc.aweme") {
            hookDouyin()
        }

        loadApp(name = "com.tencent.mm") {
            hookWeChatMessageReceive()
        }
    }

    private fun hookDouyin() {
        try {
            System.loadLibrary("dexkit")
        } catch (e: Throwable) {
            loggerD(msg = "DexKit load failed: ${e.stackTraceToString()}")
            return
        }

        val classLoader = appClassLoader ?: run {
            loggerD(msg = "ClassLoader is null")
            return
        }

        DexKitBridge.create(appInfo.sourceDir).use { bridge ->
            val baseClassData = bridge.getClassData("com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel") ?: run {
                loggerD(msg = "BaseListFragmentPanel not found")
                return
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
                return
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
                return
            }

            val baseClass = Class.forName(baseClassData.name, false, classLoader)
            val videoEventClass = Class.forName(videoEventClassData.name, false, classLoader)
            val awemeClass = Class.forName("com.ss.android.ugc.aweme.feed.model.Aweme", false, classLoader)

            val getCurrentAwemeMethod = baseClass.getDeclaredMethod(getCurrentAwemeData.methodName).apply { isAccessible = true }
            val handleVideoEventMethod = baseClass.declaredMethods.firstOrNull { method ->
                method.name == handleVideoEventData.methodName && method.parameterTypes.size == handleVideoEventData.paramTypeNames.size
            }?.apply { isAccessible = true } ?: run {
                loggerD(msg = "handleVideoEvent instance method not found")
                return
            }

            val videoEventConstructor = videoEventClass.declaredConstructors.firstOrNull { constructor ->
                val types = constructor.parameterTypes
                types.size == 2 &&
                    (types[0] == Int::class.javaPrimitiveType || types[0] == Int::class.javaObjectType) &&
                    types[1].isAssignableFrom(awemeClass)
            }?.apply { isAccessible = true } ?: run {
                loggerD(msg = "VideoEvent constructor not found")
                return
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
                            registerDownloadListener()

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
                                registerDownloadListener()

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

    /**
     * WeChat 8.0.72 receive-side hook.
     *
     * Target chain:
     * MessageSyncExtension.a(...) -> c(...) -> b(...) -> q0.f72416a (f9)
     *
     * Hooking b(...) is intentionally above MsgInfoStorage.na()/ta(): those DB methods
     * also receive UI/plugin/system-generated messages and therefore are not receive-only.
     */
    private fun hookWeChatMessageReceive() {
        try {
            System.loadLibrary("dexkit")
        } catch (e: Throwable) {
            loggerD(msg = "[WeChatHook] DexKit load failed: ${e.stackTraceToString()}")
            return
        }

        val classLoader = appClassLoader ?: run {
            loggerD(msg = "[WeChatHook] ClassLoader is null")
            return
        }

        try {
            DexKitBridge.create(appInfo.sourceDir).use { bridge ->
                val extensionClassData = bridge.findClass {
                    matcher {
                        usingStrings {
                            add(StringMatcher("MicroMsg.MessageSyncExtension", StringMatchType.Contains))
                            add(StringMatcher("processAddMsg insert db error", StringMatchType.Contains))
                        }
                    }
                }.singleOrNull() ?: run {
                    loggerD(msg = "[WeChatHook] MessageSyncExtension not found")
                    return
                }

                val receiveMethodData = bridge.findMethod {
                    searchClasses = listOf(extensionClassData)
                    matcher {
                        name = "b"
                        paramCount = 3
                        returnType = "com.tencent.mm.modelbase.q0"
                        params {
                            add("com.tencent.mm.modelbase.p0")
                            add("f35.pu4")
                            add("com.tencent.mm.modelbase.z4")
                        }
                    }
                }.singleOrNull() ?: run {
                    loggerD(msg = "[WeChatHook] MessageSyncExtension.b(...) not found")
                    return
                }

                val extensionClass = Class.forName(extensionClassData.name, false, classLoader)
                val receiveMethod = extensionClass.declaredMethods.firstOrNull { method ->
                    method.name == receiveMethodData.methodName &&
                        method.parameterTypes.size == 3 &&
                        method.returnType.name == "com.tencent.mm.modelbase.q0"
                }?.apply { isAccessible = true } ?: run {
                    loggerD(msg = "[WeChatHook] Runtime receive method not found")
                    return
                }

                findClass(extensionClass.name).hook {
                    injectMember {
                        method {
                            name = receiveMethod.name
                            param(*receiveMethod.parameterTypes.map { it.name }.toTypedArray())
                        }
                        afterHook {
                            try {
                                val q0 = result ?: return@afterHook
                                val message = findMessageInResult(q0) ?: return@afterHook
                                logReceivedMessage(message)
                            } catch (e: Throwable) {
                                loggerD(msg = "[WeChatHook] receive hook failed: ${e.stackTraceToString()}")
                            }
                        }
                    }
                }

                loggerD(msg = "[WeChatHook] message receive hook initialized: ${extensionClass.name}.${receiveMethod.name}")
            }
        } catch (e: Throwable) {
            loggerD(msg = "[WeChatHook] initialization failed: ${e.stackTraceToString()}")
        }
    }

    private fun findMessageInResult(q0: Any): Any? {
        val resultClass = q0.javaClass

        try {
            val directField = resultClass.declaredFields.firstOrNull {
                it.name == "f72416a"
            }
            if (directField != null) {
                directField.isAccessible = true
                return directField.get(q0)
            }
        } catch (_: Throwable) {
        }

        return resultClass.declaredFields.firstNotNullOfOrNull { field ->
            try {
                field.isAccessible = true
                val value = field.get(q0) ?: return@firstNotNullOfOrNull null
                if (isMessageObject(value)) value else null
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun isMessageObject(value: Any): Boolean {
        return value.javaClass.name == "com.tencent.mm.storage.f9" ||
            value.javaClass.methods.any { it.name == "getMsgId" } &&
            value.javaClass.methods.any { it.name == "getType" } &&
            value.javaClass.methods.any { it.name == "P0" }
    }

    private fun logReceivedMessage(message: Any) {
        val messageClass = message.javaClass

        fun invokeNoArg(name: String): Any? = try {
            messageClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(message)
        } catch (_: Throwable) {
            null
        }

        val talker = invokeNoArg("P0")
        val msgId = invokeNoArg("getMsgId")
        val svrId = invokeNoArg("K0")
        val msgType = invokeNoArg("getType")
        val createTime = invokeNoArg("getCreateTime")
        val isSend = invokeNoArg("F0")
        val status = invokeNoArg("O0")
        val flag = invokeNoArg("B0")

        loggerD(
            msg = "[WeChatHook][RECV] talker=$talker msgId=$msgId svrId=$svrId " +
                "type=$msgType createTime=$createTime isSend=$isSend status=$status flag=$flag"
        )
    }

    private fun registerDownloadListener() {
        val application = getCurrentApplication() ?: return
        DownloadHelper.registerClipboardListener(application)
    }

    private fun getCurrentApplication(): Application? {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val method = activityThread.getDeclaredMethod("currentApplication")
            method.isAccessible = true
            method.invoke(null) as? Application
        } catch (e: Throwable) {
            loggerD(msg = "Get current application failed: ${e.stackTraceToString()}")
            null
        }
    }
}
