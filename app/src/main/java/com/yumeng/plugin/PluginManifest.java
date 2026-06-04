package com.yumeng.plugin;

/**
 * 插件包清单
 * 对应 Operit 的 manifest.json
 */
public class PluginManifest {
    public String name;           // 插件名
    public String version;        // 版本号
    public String description;    // 描述
    public String main;           // 入口类全限定名
    public String[] permissions;  // 需要的权限
    public String author;         // 作者

    public static PluginManifest create(String name, String mainClass) {
        PluginManifest m = new PluginManifest();
        m.name = name;
        m.version = "1.0.0";
        m.main = mainClass;
        return m;
    }
}
