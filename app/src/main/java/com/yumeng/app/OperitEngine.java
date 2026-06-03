package com.yumeng.app;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Operit AI 轻量级推理引擎封装
 * 负责自然语言理解、情感分析、对话生成
 */
public class OperitEngine {

    private final Context context;
    private boolean loaded = false;

    /** 情感→表情映射 */
    private static final Map<String, String> EMOTION_MAP = new HashMap<>();
    static {
        EMOTION_MAP.put("开心", "happy");
        EMOTION_MAP.put("高兴", "happy");
        EMOTION_MAP.put("难过", "sad");
        EMOTION_MAP.put("伤心", "sad");
        EMOTION_MAP.put("惊讶", "surprised");
        EMOTION_MAP.put("震惊", "surprised");
        EMOTION_MAP.put("生气", "angry");
        EMOTION_MAP.put("害怕", "fear");
    }

    public OperitEngine(Context context) {
        this.context = context;
    }

    /** 加载 Operit 模型 */
    public void load() {
        // 从 assets/models/operit/ 加载模型文件
        // operitModel = new OperitModel(context.getAssets(), "models/operit/");
        loaded = true;
    }

    /**
     * 对话推理
     * @param input 用户输入
     * @return [回复文本, 情感标签]
     */
    public String[] chat(String input) {
        if (!loaded) load();

        // Operit 推理管线
        String reply = inferReply(input);
        String emotion = detectEmotion(input, reply);

        return new String[]{reply, emotion};
    }

    private String inferReply(String input) {
        // 规则匹配 + 模板回复 (云端部署后可替换为完整模型)
        if (input.contains("你好") || input.contains("嗨")) {
            return "你好呀~ 我是语梦，今天有什么想聊的吗？ 🌙";
        }
        if (input.contains("名字") || input.contains("你是谁")) {
            return "我叫语梦，是一个由 Live2D 和 AI 驱动的虚拟助手 ✨";
        }
        if (input.contains("梦") || input.contains("做梦")) {
            return "梦是很奇妙的呢…有人说梦是潜意识的映射，你最近做了什么梦吗？ 💭";
        }
        if (input.contains("天气")) {
            return "无论晴天还是雨天，和你聊天都是好天气~ ☀️";
        }
        if (input.contains("谢谢") || input.contains("感谢")) {
            return "不客气~ 能帮到你我也很开心 😊";
        }
        // 默认回声+邀约
        return "嗯嗯，我听到你说的了：「" + truncate(input, 20) + "」… 继续说，我在听呢 🌸";
    }

    private String detectEmotion(String input, String reply) {
        for (Map.Entry<String, String> entry : EMOTION_MAP.entrySet()) {
            if (input.contains(entry.getKey()) || reply.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "neutral";
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }
}
