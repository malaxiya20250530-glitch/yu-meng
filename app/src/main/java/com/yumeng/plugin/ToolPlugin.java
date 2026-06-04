package com.yumeng.plugin;

/**
 * 插件基类
 * 对应 Operit 的 exports.main = function(host) { ... }
 * 插件开发者继承此类，实现 onInstall() 和 onInvoke()
 */
public abstract class ToolPlugin {

    protected IToolHost host;
    protected PluginManifest manifest;

    /** 插件安装时调用 — 对应 exports.main(host) */
    public final void init(IToolHost host, PluginManifest manifest) {
        this.host = host;
        this.manifest = manifest;
        onInstall();
    }

    /** 子类实现：插件初始化逻辑 */
    public abstract void onInstall();

    /** 子类实现：收到工具调用 */
    public abstract ToolResult onInvoke(String action, java.util.Map<String, Object> args);

    /** 获取宿主能力 — 对应宿主注入的 Tools 对象 */
    protected IToolHost tools() { return host; }

    /** 完成并返回结果 */
    protected void complete(ToolResult result) { host.complete(result); }
}
