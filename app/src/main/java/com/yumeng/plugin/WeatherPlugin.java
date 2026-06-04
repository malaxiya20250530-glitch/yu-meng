package com.yumeng.plugin;

import java.util.Map;

/**
 * 天气查询插件 — 内置示例
 * 对应 Operit 的 examples/quick_start.ts
 */
public class WeatherPlugin extends ToolPlugin {

    @Override
    public void onInstall() {
        tools().log("info", "天气插件已就绪");
    }

    @Override
    public ToolResult onInvoke(String action, Map<String, Object> args) {
        if ("query".equals(action)) {
            String city = (String) args.getOrDefault("city", "北京");
            // 模拟天气数据
            String[] weathers = {"晴 ☀️", "多云 ⛅", "小雨 🌧️", "阴天 ☁️"};
            int temp = 18 + (city.hashCode() & 0xF);
            String w = weathers[Math.abs(city.hashCode()) % weathers.length];
            return ToolResult.ok()
                .with("city", city)
                .with("weather", w)
                .with("temperature", temp + "°C")
                .with("humidity", (40 + Math.abs(city.hashCode()) % 40) + "%");
        }
        return ToolResult.fail("天气插件仅支持 query 操作");
    }
}
