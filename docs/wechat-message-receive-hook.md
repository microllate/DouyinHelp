# WeChat message receive hook

This branch adds a WeChat 8.0.72 receive-side probe to the existing DouyinHelp YukiHookAPI + DexKit module.

## Target

`com.tencent.mm.plugin.messenger.foundation.a2.b(p0, pu4, z4): q0`

The implementation dynamically locates the `MessageSyncExtension` class using stable strings, then resolves the receive method using its return type and parameter types.

The hook runs after the method returns and extracts the `com.tencent.mm.storage.f9` message from the returned `q0` object. It only logs message metadata and does not modify the message or return value.

## Log format

```text
[WeChatHook][RECV] talker=... msgId=... svrId=... type=... createTime=... isSend=... status=... flag=...
```

## Why this layer

`h9.na(f9)` and `h9.ta(f9, false)` are database/storage operations and have callers outside network receive processing. Hooking them would therefore produce false positives for locally generated/system/plugin messages.

The selected `a2.b(...)` layer is above database insertion and already exposes the parsed `f9` message through `q0`, which is a better receive-side interception point.

## Runtime validation

1. Enable the built module for WeChat in LSPosed.
2. Force-stop WeChat and reopen it.
3. Trigger a new incoming message.
4. Filter LSPosed/Xposed logs for `[WeChatHook]`.
5. Confirm `talker`, `msgId`, `svrId`, `type`, and `createTime` are populated.

This is currently a non-invasive probe. The next functional layer can consume the extracted `f9` object without changing the hook point.
