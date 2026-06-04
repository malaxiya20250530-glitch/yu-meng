package com.yumeng.plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具调用返回值
 * 对应 Operit 的 complete({ success: true, data: ... })
 */
public class ToolResult {
    public boolean success;
    public String error;
    public Map<String, Object> data = new HashMap<>();

    public static ToolResult ok() {
        ToolResult r = new ToolResult();
        r.success = true;
        return r;
    }

    public static ToolResult ok(String key, Object value) {
        ToolResult r = new ToolResult();
        r.success = true;
        r.data.put(key, value);
        return r;
    }

    public static ToolResult fail(String error) {
        ToolResult r = new ToolResult();
        r.success = false;
        r.error = error;
        return r;
    }

    public ToolResult with(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        if (success) return "OK: " + data;
        return "FAIL: " + error;
    }
}
