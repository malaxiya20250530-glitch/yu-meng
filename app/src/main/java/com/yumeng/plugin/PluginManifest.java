package com.yumeng.plugin;

/**
 * 插件包清单 — 对应 Operit 的 manifest.json
 */
public class PluginManifest {
    public String name;
    public String version;
    public String description;
    public String main;
    public String[] permissions;
    public String author;

    public static PluginManifest create(String name, String mainClass) {
        PluginManifest m = new PluginManifest();
        m.name = name;
        m.version = "1.0.0";
        m.main = mainClass;
        return m;
    }

    public PluginManifest description(String desc) {
        this.description = desc;
        return this;
    }

    public PluginManifest version(String v) {
        this.version = v;
        return this;
    }
}
