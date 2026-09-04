package com.example.douyinhelp.hook

import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.loggerD
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.base.StringMatcher


object DoubleClickCommentHook {


    fun hook(
        bridge: DexKitBridge,
        classLoader: ClassLoader
    ) {


        // ============================================================
        // BaseListFragmentPanel
        // ============================================================

        val baseClassData =
            bridge.getClassData(
                "com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel"
            )
                ?: run {
                    loggerD("BaseListFragmentPanel 未找到")
                    return
                }



        // ============================================================
        // handleDoubleClick
        // ============================================================

        val handleDoubleClickData =
            bridge.findMethod {

                searchClasses = listOf(baseClassData)

                matcher {

                    name = "handleDoubleClick"

                    paramCount = 1

                }

            }.singleOrNull()



        // ============================================================
        // handleVideoEvent
        // ============================================================

        val handleVideoEventData =
            bridge.findMethod {

                searchClasses = listOf(baseClassData)

                matcher {

                    name = "handleVideoEvent"

                    paramCount = 1

                }

            }.singleOrNull()



        // ============================================================
        // getCurrentAweme
        // ============================================================

        val getCurrentAwemeData =
            bridge.findMethod {

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

            loggerD("双击评论方法定位失败")
            return

        }



        // ============================================================
        // VideoEvent
        // ============================================================

        val videoEventClassData =
            bridge.findClass {

                matcher {

                    usingStrings {

                        add(
                            StringMatcher(
                                "VideoEvent",
                                StringMatchType.Contains
                            )
                        )

                    }

                }

            }.singleOrNull()



        if(videoEventClassData == null){

            loggerD("VideoEvent 未找到")
            return

        }



        val panelClass =
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
        // VideoEvent(int, Aweme)
        // ============================================================

        val constructor =
            videoEventClass
                .declaredConstructors
                .firstOrNull {


                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType

                }


        if(constructor == null){

            loggerD("VideoEvent构造失败")
            return

        }


        constructor.isAccessible = true



        // ============================================================
        // Hook 双击
        // ============================================================

        panelClass.method {

            name =
                handleDoubleClickData.methodName

        }.hook {


            before {


                try {


                    val awemeMethod =
                        panelClass.getDeclaredMethod(
                            getCurrentAwemeData.methodName
                        )


                    awemeMethod.isAccessible = true


                    val aweme =
                        awemeMethod.invoke(instance)



                    if(aweme == null)
                        return@before



                    // 7 = 打开评论事件

                    val event =
                        constructor.newInstance(
                            7,
                            aweme
                        )



                    val videoMethod =
                        panelClass
                            .declaredMethods
                            .firstOrNull {

                                it.name ==
                                        handleVideoEventData.methodName &&
                                it.parameterTypes.size == 1

                            }



                    videoMethod?.apply {

                        isAccessible = true

                        invoke(
                            instance,
                            event
                        )

                    }



                    //取消点赞

                    resultNull()



                    loggerD(
                        "双击评论成功"
                    )


                }
                catch(e:Throwable){

                    loggerD(
                        "双击评论异常:${e.message}"
                    )

                }


            }

        }



        loggerD(
            "DoubleClickCommentHook安装完成"
        )

    }

}
