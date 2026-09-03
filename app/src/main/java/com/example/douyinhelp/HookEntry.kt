package com.example.douyinhelp

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

                // 查找双击手势类
                val doubleTapClassName = bridge.findClass {
                    matcher {
                        usingStrings("onDoubleTap")
                    }
                }.firstOrNull()?.name

                // 查找 EventBus
                val eventBusClassData = bridge.findClass {
                    matcher {
                        usingStrings("No subscribers registered for event")
                    }
                }.firstOrNull()

                // 查找评论事件类
                val commentEventClassName = bridge.findClass {
                    matcher {
                        usingStrings("/douyin/comment")
                    }
                }.firstOrNull()?.name

                if (
                    doubleTapClassName == null ||
                    eventBusClassData == null ||
                    commentEventClassName == null
                ) {
                    loggerD(
                        msg = "DexKit 匹配失败，请检查当前抖音版本特征"
                    )
                    return@loadApp
                }

                loggerD(msg = "双击类: $doubleTapClassName")
                loggerD(msg = "评论事件类: $commentEventClassName")
                loggerD(msg = "EventBus 类: ${eventBusClassData.name}")

                val classLoader = appClassLoader ?: run {
                    loggerD(msg = "获取 App ClassLoader 失败")
                    return@loadApp
                }

                val eventBusClass = eventBusClassData.getInstance(classLoader)

                // 使用 Java Class，避免和 YukiHookAPI 的 KClass 混淆
                val commentEventJavaClass =
                    Class.forName(commentEventClassName, false, classLoader)

                // Hook 双击
                findClass(doubleTapClassName).hook {
                    injectMember {
                        method {
                            name = "onDoubleTap"
                        }

                        beforeHook {

                            // 阻止原来的双击点赞
                            resultFalse()

                            try {

                                // 查找 Aweme 参数
                                val awemeObj = args.firstOrNull {
                                    it != null &&
                                        it.javaClass.name.contains("Aweme")
                                }

                                if (awemeObj == null) {
                                    loggerD(msg = "未找到 Aweme 参数")
                                    return@beforeHook
                                }

                                /*
                                 * 查找评论事件构造函数
                                 */
                                val constructor =
                                    commentEventJavaClass.declaredConstructors
                                        .firstOrNull { ctor ->
                                            val parameterTypes = ctor.parameterTypes

                                            parameterTypes.size == 1 &&
                                                parameterTypes[0]
                                                    .isAssignableFrom(
                                                        awemeObj.javaClass
                                                    )
                                        }

                                if (constructor == null) {
                                    loggerD(
                                        msg = "未找到兼容 Aweme 的评论事件构造函数"
                                    )
                                    return@beforeHook
                                }

                                constructor.isAccessible = true

                                val commentEvent =
                                    constructor.newInstance(awemeObj)

                                /*
                                 * 获取 EventBus 单例
                                 */
                                val eventBusInstance =
                                    eventBusClass
                                        .method {
                                            name = "getDefault"
                                        }
                                        .get()
                                        .invoke()

                                /*
                                 * 查找 post 方法
                                 */
                                val postMethod =
                                    eventBusClass.java.methods
                                        .firstOrNull { post ->
                                            post.name == "post" &&
                                                post.parameterTypes.size == 1 &&
                                                post.parameterTypes[0]
                                                    .isAssignableFrom(
                                                        commentEvent.javaClass
                                                    )
                                        }

                                if (postMethod == null) {
                                    loggerD(
                                        msg = "未找到兼容评论事件的 EventBus.post 方法"
                                    )
                                    return@beforeHook
                                }

                                postMethod.isAccessible = true

                                postMethod.invoke(
                                    eventBusInstance,
                                    commentEvent
                                )

                                loggerD(
                                    msg = "成功拦截双击，已发送评论区事件"
                                )

                            } catch (e: Throwable) {

                                loggerD(
                                    msg = "打开评论区异常: ${e.stackTraceToString()}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
