package com.example.douyinhelp

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = true
    }

    override fun onHook() = encase {
        loadApp(name = "com.ss.android.ugc.aweme") {
            // 拦截/修改双击点赞手势
            findClass("com.ss.android.ugc.aweme.feed.ui.FeedDoubleTapDiggHooker")
                .hook {
                    injectMember {
                        method {
                            name = "onClick"
                        }
                        beforeHook {
                            // 阻断默认双击点赞动作
                            resultNull()
                        }
                    }
                }

            // 修改双击打开评论区手势
            findClass("com.ss.android.ugc.aweme.feed.ui.FeedDoubleTapOpenCommentHooker")
                .hook {
                    injectMember {
                        method {
                            name = "onClick"
                        }
                        beforeHook {
                            // 可在此处自定义双击打开评论区的响应逻辑
                        }
                    }
                }
        }
    }
}
