package com.yumeng.plugin;

import java.util.Map;

/**
 * 宿主 API 契约接口
 * 对应 Operit 的 examples/types/index.d.ts
 * 插件通过此接口调用宿主能力（文件/网络/系统等）
 */
public interface IToolHost {

    /** 工具调用入口 — 对应 toolCall(name, args) */
    ToolResult toolCall(String name, Map<String, Object> args);

    /** 休眠 — 对应 Tools.System.sleep(ms) */
    void sleep(long ms);

    /** HTTP GET — 对应 Tools.Network.fetch(url) */
    String fetch(String url);

    /** 文件读取 — 对应 Tools.Files.read(path) */
    String readFile(String path);

    /** 文件写入 — 对应 Tools.Files.write(path, content) */
    void writeFile(String path, String content);

    /** 获取配置 — 对应 Tools.Config.get(key) */
    String getConfig(String key);

    /** 日志输出 — 对应 Tools.Logger.log(msg) */
    void log(String level, String msg);

    /** 完成回调 — 对应 complete(result) */
    void complete(ToolResult result);
}
