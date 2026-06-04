package com.yumeng.plugin;

import android.content.Context;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件管理器 — 对应 Operit 的 ToolPkg 注册与路由
 * 职责：安装/卸载插件、路由工具调用、维护插件注册表
 */
public class PluginManager implements IToolHost {

    private final Context context;
    private final Map<String, ToolPlugin> registry = new LinkedHashMap<>();
    private final Map<String, PluginManifest> manifests = new LinkedHashMap<>();
    private final List<String> callLog = new ArrayList<>();

    public PluginManager(Context context) {
        this.context = context;
    }

    /** 注册内置插件 — 对应 ToolPkg 安装流程 */
    public PluginManager install(ToolPlugin plugin, PluginManifest manifest) {
        String name = manifest.name;
        plugin.init(this, manifest);
        registry.put(name, plugin);
        manifests.put(name, manifest);
        log("info", "插件已安装: " + name + " v" + manifest.version);
        return this;
    }

    /** 卸载插件 */
    public void uninstall(String name) {
        registry.remove(name);
        manifests.remove(name);
        log("info", "插件已卸载: " + name);
    }

    /** 获取已安装插件列表 */
    public List<PluginManifest> listPlugins() {
        return new ArrayList<>(manifests.values());
    }

    /** 路由工具调用到对应插件 */
    public ToolResult invokePlugin(String pluginName, String action, Map<String, Object> args) {
        ToolPlugin plugin = registry.get(pluginName);
        if (plugin == null) {
            return ToolResult.fail("插件未找到: " + pluginName);
        }
        try {
            ToolResult result = plugin.onInvoke(action, args);
            callLog.add(pluginName + "." + action + " -> " + (result.success ? "OK" : "FAIL"));
            return result;
        } catch (Exception e) {
            return ToolResult.fail("插件异常: " + e.getMessage());
        }
    }

    /** 获取调用日志 */
    public List<String> getCallLog() { return callLog; }

    // ==================== IToolHost 实现 ====================

    @Override
    public ToolResult toolCall(String name, Map<String, Object> args) {
        // 内置工具
        if ("list_plugins".equals(name)) {
            ToolResult r = ToolResult.ok();
            List<String> names = new ArrayList<>(registry.keySet());
            r.data.put("plugins", names);
            return r;
        }
        if ("get_time".equals(name)) {
            return ToolResult.ok("time", System.currentTimeMillis());
        }
        if ("get_call_log".equals(name)) {
            return ToolResult.ok("log", new ArrayList<>(callLog));
        }
        return ToolResult.fail("未知工具: " + name);
    }

    @Override
    public void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    @Override
    public String fetch(String url) {
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            okhttp3.Request req = new okhttp3.Request.Builder().url(url).build();
            okhttp3.Response res = client.newCall(req).execute();
            return res.body() != null ? res.body().string() : "";
        } catch (Exception e) {
            return "FETCH_ERROR: " + e.getMessage();
        }
    }

    @Override
    public String readFile(String path) {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(context.getAssets().open(path)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "READ_ERROR: " + e.getMessage();
        }
    }

    @Override
    public void writeFile(String path, String content) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(
                new java.io.File(context.getFilesDir(), path));
            fw.write(content);
            fw.close();
        } catch (Exception ignored) {}
    }

    @Override
    public String getConfig(String key) {
        return context.getSharedPreferences("yumeng_plugins", 0).getString(key, null);
    }

    @Override
    public void log(String level, String msg) {
        android.util.Log.println(
            "INFO".equals(level) ? android.util.Log.INFO : android.util.Log.ERROR,
            "YuMengPlugin", msg);
    }

    @Override
    public void complete(ToolResult result) {
        callLog.add("complete: " + (result.success ? "OK" : result.error));
    }
}
