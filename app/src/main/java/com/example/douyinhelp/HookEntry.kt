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

            val classLoader = appClassLoader ?: run {
                loggerD(msg = "获取 App ClassLoader 失败")
                return@loadApp
            }

            DexKitBridge.create(apkPath).use { bridge ->

                // 查找双击手势类
                val doubleTapClassName: String = bridge.findClass {
                    matcher {
                        usingStrings("onDoubleTap")
                    }
                }.firstOrNull()?.name?: run {
                    loggerD(msg = "未找到双击类")
                    return@loadApp
                } 

                // 查找 EventBus
                val eventBusClassName = bridge.findClass {
                    matcher {
                        usingStrings("No subscribers registered for event")
                    }
                }.firstOrNull()?.name

                // 查找评论事件类
                val commentEventClassName = bridge.findClass {
                    matcher {
                        usingStrings("/douyin/comment")
                    }
                }.firstOrNull()?.name

                if (
                    doubleTapClassName == null ||
                    eventBusClassName == null ||
                    commentEventClassName == null
                ) {
                    loggerD(
                        msg = "DexKit 匹配失败，请检查当前抖音版本特征"
                    )
                    return@loadApp
                }

                loggerD(msg = "双击类: $doubleTapClassName")
                loggerD(msg = "评论事件类: $commentEventClassName")
                loggerD(msg = "EventBus 类: $eventBusClassName")

                /*
                 * 全部使用标准 Java Class
                 * 不再混用 YukiHookAPI KClass
                 */
                val eventBusJavaClass =
                    Class.forName(
                        eventBusClassName,
                        false,
                        classLoader
                    )

                val commentEventJavaClass =
                    Class.forName(
                        commentEventClassName,
                        false,
                        classLoader
                    )

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

                                /*
                                 * 查找 Aweme 参数
                                 */
                                val awemeObj: Any? = args.firstOrNull { arg ->
                                    arg != null &&
                                        arg.javaClass.name.contains("Aweme")
                                }

                                if (awemeObj == null) {
                                    loggerD(msg = "未找到 Aweme 参数")
                                    return@beforeHook
                                }

                                /*
                                 * 查找评论事件构造函数
                                 */
                                val constructor: java.lang.reflect.Constructor<*>? =
                                    commentEventJavaClass.declaredConstructors
                                        .firstOrNull { ctor ->
                                            val parameterTypes =
                                                ctor.parameterTypes

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

                                val commentEvent: Any =
                                    constructor.newInstance(awemeObj)

                                /*
                                 * 获取 EventBus 单例
                                 *
                                 * 直接使用 Java 反射，
                                 * 不再调用 YukiHookAPI 的 method()
                                 */
                                val getDefaultMethod:
                                    java.lang.reflect.Method? =
                                    eventBusJavaClass.declaredMethods
                                        .firstOrNull { m ->
                                            m.name == "getDefault" &&
                                                m.parameterTypes.isEmpty()
                                        }

                                if (getDefaultMethod == null) {
                                    loggerD(
                                        msg = "未找到 EventBus.getDefault()"
                                    )
                                    return@beforeHook
                                }

                                getDefaultMethod.isAccessible = true

                                val eventBusInstance: Any =
                                    getDefaultMethod.invoke(null)
                                        ?: run {
                                            loggerD(
                                                msg = "EventBus.getDefault() 返回为空"
                                            )
                                            return@beforeHook
                                        }

                                /*
                                 * 查找 post 方法
                                 */
                                val postMethod:
                                    java.lang.reflect.Method? =
                                    eventBusJavaClass.methods
                                        .firstOrNull { m ->
                                            m.name == "post" &&
                                                m.parameterTypes.size == 1 &&
                                                m.parameterTypes[0]
                                                    .isAssignableFrom(
                                                        commentEvent.javaClass
                                                    )
                                        }

                                if (postMethod == null) {
                                    loggerD(
                                        msg = "未找到兼容评论事件的 EventBus.post()"
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
