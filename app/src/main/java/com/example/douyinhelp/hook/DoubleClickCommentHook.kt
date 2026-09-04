package com.example.douyinhelp.hook

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.base.StringMatcher
import java.lang.reflect.Constructor


data class DoubleClickInfo(

    val baseClassName: String,

    val handleDoubleClickName: String,

    val handleDoubleClickParams: Array<String>,

    val handleVideoEventName: String,

    val getCurrentAwemeName: String,

    val videoEventConstructor: Constructor<*>

)



object DoubleClickCommentHook {


    fun find(
        bridge: DexKitBridge,
        classLoader: ClassLoader
    ): DoubleClickInfo? {


        // ============================================================
        // 1. BaseListFragmentPanel
        // ============================================================

        val baseClassData =
            bridge.getClassData(
                "com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel"
            )
            ?: return null



        // handleDoubleClick(MotionEvent)

        val handleDoubleClickData =
            bridge.findMethod {

                searchClasses =
                    listOf(baseClassData)


                matcher {

                    name = "handleDoubleClick"


                    params {

                        add(
                            "android.view.MotionEvent"
                        )

                    }

                }

            }.singleOrNull()



        // handleVideoEvent

        val handleVideoEventData =
            bridge.findMethod {

                searchClasses =
                    listOf(baseClassData)


                matcher {

                    name = "handleVideoEvent"

                    paramCount = 1

                    returnType = "void"

                }

            }.singleOrNull()



        // getCurrentAweme

        val getCurrentAwemeData =
            bridge.findMethod {

                searchClasses =
                    listOf(baseClassData)


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

            return null

        }



        // ============================================================
        // 2. VideoEvent
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


                        add(
                            StringMatcher(
                                "param",
                                StringMatchType.Contains
                            )
                        )


                        add(
                            StringMatcher(
                                "videoType",
                                StringMatchType.Contains
                            )
                        )


                        add(
                            StringMatcher(
                                "isPlaying",
                                StringMatchType.Contains
                            )
                        )

                    }


                    methods {

                        add {

                            name = "toString"

                        }

                    }

                }

            }.singleOrNull()
            ?: return null




        val videoEventClass =
            Class.forName(
                videoEventClassData.name,
                false,
                classLoader
            )



        val awemeClass =
            Class.forName(
                "com.ss.android.ugc.aweme.feed.model.Aweme",
                false,
                classLoader
            )



        // ============================================================
        // 3. VideoEvent(int, Aweme)
        // ============================================================

        val constructor =
            videoEventClass
                .declaredConstructors
                .firstOrNull {


                    val types =
                        it.parameterTypes


                    types.size == 2 &&

                    (
                        types[0] ==
                                Int::class.javaPrimitiveType ||

                        types[0] ==
                                Int::class.javaObjectType
                    )

                    &&

                    types[1].isAssignableFrom(
                        awemeClass
                    )


                }
                ?: return null



        constructor.isAccessible = true



        return DoubleClickInfo(

            baseClassName =
                baseClassData.name,


            handleDoubleClickName =
                handleDoubleClickData.methodName,


            handleDoubleClickParams =
                handleDoubleClickData.paramTypeNames,


            handleVideoEventName =
                handleVideoEventData.methodName,


            getCurrentAwemeName =
                getCurrentAwemeData.methodName,


            videoEventConstructor =
                constructor

        )

    }

}
