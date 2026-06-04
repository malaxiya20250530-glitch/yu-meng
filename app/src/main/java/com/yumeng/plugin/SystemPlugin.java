package com.yumeng.plugin;

import java.util.Map;

/**
 * 系统信息插件
 */
public class SystemPlugin extends ToolPlugin {

    @Override
    public void onInstall() {
        tools().log("info", "系统插件已就绪");
    }

    @Override
    public ToolResult onInvoke(String action, Map<String, Object> args) {
        if ("info".equals(action)) {
            Runtime rt = Runtime.getRuntime();
            return ToolResult.ok()
                .with("os", "Android " + android.os.Build.VERSION.RELEASE)
                .with("model", android.os.Build.MODEL)
                .with("available_memory_mb", rt.freeMemory() / 1024 / 1024)
                .with("total_memory_mb", rt.totalMemory() / 1024 / 1024);
        }
        if ("time".equals(action)) {
            return ToolResult.ok("datetime", new java.util.Date().toString());
        }
        return ToolResult.fail("系统插件支持: info, time");
    }
}
