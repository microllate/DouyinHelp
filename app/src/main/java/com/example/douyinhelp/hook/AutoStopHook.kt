package com.example.douyinhelp.hook

import android.util.Log
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.MotionEventClass
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.base.StringMatcher


object AutoStopHook {

    private const val TAG = "AutoStopHook"

    // 抖音播放完成事件
    private const val EVENT_PLAY_COMPLETED = 7


    fun hook(
        bridge: DexKitBridge,
        classLoader: ClassLoader
    ) {


        // =====================================================
        // 1. 获取 BaseListFragmentPanel
        // =====================================================

        val baseClassData = bridge.getClassData(
            "com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel"
        )


        if (baseClassData == null) {
            Log.e(TAG, "BaseListFragmentPanel not found")
            return
        }



        // =====================================================
        // 2. 查找 onVideoPlayerEvent
        // =====================================================

        val videoEventMethod = bridge.findMethod {

            searchClasses = listOf(baseClassData)

            matcher {

                name = "onVideoPlayerEvent"

                paramCount = 1

                returnType = "void"

            }

        }.singleOrNull()



        if (videoEventMethod == null) {

            Log.e(TAG, "onVideoPlayerEvent not found")

            return
        }



        // =====================================================
        // 3. 查找暂停方法
        // =====================================================

        val pauseMethod = bridge.findMethod {


            searchClasses = listOf(baseClassData)


            matcher {

                name = "pauseCurrentPlayerWithListener"

                paramCount = 0

                returnType = "void"

            }


        }.singleOrNull()



        if (pauseMethod == null) {

            Log.e(TAG, "pauseCurrentPlayerWithListener not found")

            return
        }



        val showPauseMethod = bridge.findMethod {


            searchClasses = listOf(baseClassData)


            matcher {

                name = "showIvWhenPause"

                paramCount = 0

                returnType = "void"

            }


        }.singleOrNull()



        Log.d(TAG, "AutoStop hook ready")



        // =====================================================
        // 4. Hook播放事件
        // =====================================================


        try {


            Class.forName(
                baseClassData.name,
                false,
                classLoader
            ).method {

                name = videoEventMethod.methodName

            }.hook {


                after {


                    val event = args[0]
                        ?: return@after



                    // VideoPlayerEvent.code字段
                    val codeField =
                        event.javaClass.declaredFields
                            .firstOrNull {

                                it.type == Int::class.javaPrimitiveType

                            }
                            ?: return@after



                    codeField.isAccessible = true


                    val code =
                        codeField.getInt(event)



                    if (code != EVENT_PLAY_COMPLETED) {

                        return@after

                    }



                    Log.d(
                        TAG,
                        "video completed, pause"
                    )



                    //暂停视频

                    pauseMethod.invoke(instance)



                    //显示暂停按钮

                    showPauseMethod?.invoke(instance)


                }


            }



        } catch (e: Throwable) {

            Log.e(
                TAG,
                "hook failed",
                e
            )

        }

    }

}
