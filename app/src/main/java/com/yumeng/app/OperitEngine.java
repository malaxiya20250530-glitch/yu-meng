package com.yumeng.app;

import android.os.Handler;
import android.os.Looper;
import com.yumeng.plugin.PluginManager;
import com.yumeng.plugin.ToolResult;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Operit AI 推理引擎
 * LLM 调用 + 规则回退 + 插件路由
 */
public class OperitEngine {

    public interface ChatCallback {
        void onReply(String text, String emotion);
        void onError(String error);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient httpClient;
    private PluginManager pluginManager;

    private String apiBaseUrl = "http://localhost:11434/v1";
    private String apiKey = "ollama";
    private String model = "qwen2.5:7b";
    private boolean llmEnabled = true;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final Map<String, String> EMOTION_MAP = new HashMap<>();
    static {
        EMOTION_MAP.put("开心", "happy"); EMOTION_MAP.put("高兴", "happy"); EMOTION_MAP.put("快乐", "happy");
        EMOTION_MAP.put("难过", "sad"); EMOTION_MAP.put("伤心", "sad"); EMOTION_MAP.put("悲伤", "sad");
        EMOTION_MAP.put("惊讶", "surprised"); EMOTION_MAP.put("震惊", "surprised");
        EMOTION_MAP.put("生气", "angry"); EMOTION_MAP.put("愤怒", "angry");
        EMOTION_MAP.put("害怕", "fear"); EMOTION_MAP.put("恐惧", "fear");
    }

    public OperitEngine() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public void setPluginManager(PluginManager pm) { this.pluginManager = pm; }

    public void configLLM(String baseUrl, String key, String modelName) {
        if (baseUrl != null) this.apiBaseUrl = baseUrl;
        if (key != null) this.apiKey = key;
        if (model != null) this.model = modelName;
    }

    // ==================== 同步接口(兼容旧) ====================

    public String[] chat(String input) {
        // 先尝试插件路由
        String[] pluginResult = tryPluginRoute(input);
        if (pluginResult != null) return pluginResult;

        if (llmEnabled) {
            try {
                String reply = callLLMSync(input);
                if (reply != null && !reply.isEmpty()) {
                    return new String[]{reply, detectEmotion(input, reply)};
                }
            } catch (Exception ignored) {}
        }
        return ruleChat(input);
    }

    // ==================== 异步接口(推荐) ====================

    public void chatAsync(String input, ChatCallback callback) {
        // 先尝试插件路由
        String[] pluginResult = tryPluginRoute(input);
        if (pluginResult != null) {
            callback.onReply(pluginResult[0], pluginResult[1]);
            return;
        }

        if (!llmEnabled) {
            String[] r = ruleChat(input);
            callback.onReply(r[0], r[1]);
            return;
        }
        callLLMAsync(input, callback);
    }

    // ==================== 插件路由层 ====================

    private String[] tryPluginRoute(String input) {
        if (pluginManager == null) return null;

        // 天气
        if (input.contains("天气") || input.contains("气温")) {
            String city = extractCity(input);
            Map<String, Object> args = new HashMap<>();
            args.put("city", city);
            ToolResult r = pluginManager.invokePlugin("weather", "query", args);
            if (r.success) {
                String reply = r.data.get("city") + "天气: " + r.data.get("weather")
                    + "，气温" + r.data.get("temperature") + "，湿度" + r.data.get("humidity");
                return new String[]{reply, detectEmotion(input, reply)};
            }
        }

        // 计算器
        if (input.matches(".*[\\d]+\\s*[+\\-*/]\\s*[\\d]+.*")) {
            String expr = input.replaceAll("[^0-9.+\\-*/()]", "").trim();
            if (!expr.isEmpty()) {
                Map<String, Object> args = new HashMap<>();
                args.put("expr", expr);
                ToolResult r = pluginManager.invokePlugin("calculator", "calc", args);
                if (r.success) {
                    String reply = expr + " = " + r.data.get("result");
                    return new String[]{reply, "happy"};
                }
            }
        }

        // 系统信息
        if (input.contains("系统") || input.contains("内存") || input.contains("设备")) {
            ToolResult r = pluginManager.invokePlugin("system", "info", new HashMap<>());
            if (r.success) {
                String reply = "设备: " + r.data.get("model") + "\n系统: " + r.data.get("os")
                    + "\n可用内存: " + r.data.get("available_memory_mb") + "MB";
                return new String[]{reply, "neutral"};
            }
        }

        // 时间
        if (input.contains("几点") || input.contains("时间") || input.contains("日期")) {
            ToolResult r = pluginManager.invokePlugin("system", "time", new HashMap<>());
            if (r.success) {
                return new String[]{"现在是 " + r.data.get("datetime"), "neutral"};
            }
        }

        return null;
    }

    private String extractCity(String input) {
        String[] cities = {"北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉", "西安", "重庆", "东京", "纽约", "伦敦"};
        for (String c : cities) {
            if (input.contains(c)) return c;
        }
        return "北京";
    }

    // ==================== LLM 调用 ====================

    private String callLLMSync(String input) throws Exception {
        Request request = buildLLMRequest(input);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            ResponseBody body = response.body();
            if (body == null) return null;
            JSONObject jsonResp = new JSONObject(body.string());
            JSONArray choices = jsonResp.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                if (msg != null) return msg.optString("content", "").trim();
            }
        }
        return null;
    }

    private void callLLMAsync(String input, ChatCallback callback) {
        httpClient.newCall(buildLLMRequest(input)).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String[] r = ruleChat(input);
                mainHandler.post(() -> callback.onReply(r[0], r[1]));
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) { fallback(input, callback); return; }
                    JSONObject jsonResp = new JSONObject(body.string());
                    JSONArray choices = jsonResp.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
                        if (msg != null) {
                            String content = msg.optString("content", "").trim();
                            if (!content.isEmpty()) {
                                String emotion = detectEmotion(input, content);
                                mainHandler.post(() -> callback.onReply(content, emotion));
                                return;
                            }
                        }
                    }
                    fallback(input, callback);
                } catch (Exception e) { fallback(input, callback); }
            }
            private void fallback(String input, ChatCallback cb) {
                String[] r = ruleChat(input);
                mainHandler.post(() -> cb.onReply(r[0], r[1]));
            }
        });
    }

    private Request buildLLMRequest(String input) {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            JSONArray msgs = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "你是语梦，温柔可爱的虚拟助手。用中文回复，简洁亲切。");
            msgs.put(sys);
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", input);
            msgs.put(user);
            body.put("messages", msgs);
            body.put("temperature", 0.8);
            body.put("max_tokens", 200);
        } catch (Exception ignored) {}
        return new Request.Builder()
                .url(apiBaseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
    }

    // ==================== 规则引擎(回退) ====================

    private String[] ruleChat(String input) {
        String reply = inferRuleReply(input);
        return new String[]{reply, detectEmotion(input, reply)};
    }

    private String inferRuleReply(String input) {
        if (input.contains("你好") || input.contains("嗨")) return "你好呀~ 我是语梦 🌙";
        if (input.contains("你是谁")) return "我叫语梦，AI虚拟助手 ✨";
        if (input.contains("谢谢")) return "不客气~ 😊";
        if (input.contains("再见") || input.contains("拜拜")) return "下次聊哦~ 👋";
        return "嗯嗯，「" + truncate(input, 20) + "」… 继续说，我在听 🌸";
    }

    private String detectEmotion(String input, String reply) {
        String combined = input + " " + reply;
        for (Map.Entry<String, String> e : EMOTION_MAP.entrySet()) {
            if (combined.contains(e.getKey())) return e.getValue();
        }
        return "neutral";
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
