package com.yumeng.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
 * 支持 OpenAI/Ollama 兼容 API + 本地规则回退
 */
public class OperitEngine {

    public interface ChatCallback {
        void onReply(String text, String emotion);
        void onError(String error);
    }

    private final Context context;
    private final OkHttpClient httpClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // LLM 配置（默认 Ollama 本地）
    private String apiBaseUrl = "http://localhost:11434/v1";
    private String apiKey = "ollama";
    private String model = "qwen2.5:7b";
    private boolean llmEnabled = true;
    private int timeoutSeconds = 30;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** 情感→表情映射 */
    private static final Map<String, String> EMOTION_MAP = new HashMap<>();
    static {
        EMOTION_MAP.put("开心", "happy");
        EMOTION_MAP.put("高兴", "happy");
        EMOTION_MAP.put("快乐", "happy");
        EMOTION_MAP.put("难过", "sad");
        EMOTION_MAP.put("伤心", "sad");
        EMOTION_MAP.put("悲伤", "sad");
        EMOTION_MAP.put("惊讶", "surprised");
        EMOTION_MAP.put("震惊", "surprised");
        EMOTION_MAP.put("生气", "angry");
        EMOTION_MAP.put("愤怒", "angry");
        EMOTION_MAP.put("害怕", "fear");
        EMOTION_MAP.put("恐惧", "fear");
    }

    public OperitEngine(Context context) {
        this.context = context;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    /** 配置 LLM 连接参数 */
    public void configLLM(String baseUrl, String key, String modelName) {
        this.apiBaseUrl = baseUrl != null ? baseUrl : this.apiBaseUrl;
        this.apiKey = key != null ? key : this.apiKey;
        this.model = modelName != null ? modelName : this.model;
    }

    /** 启用/禁用 LLM */
    public void setLLMEnabled(boolean enabled) {
        this.llmEnabled = enabled;
    }

    /** 同步对话（兼容旧接口） */
    public String[] chat(String input) {
        if (llmEnabled) {
            try {
                String llmReply = callLLMSync(input);
                if (llmReply != null && !llmReply.isEmpty()) {
                    String emotion = detectEmotion(input, llmReply);
                    return new String[]{llmReply, emotion};
                }
            } catch (Exception e) {
                // LLM 失败，回退到规则引擎
            }
        }
        return ruleChat(input);
    }

    /** 异步对话（推荐） */
    public void chatAsync(String input, ChatCallback callback) {
        if (!llmEnabled) {
            String[] result = ruleChat(input);
            callback.onReply(result[0], result[1]);
            return;
        }

        callLLMAsync(input, callback);
    }

    /** 同步 LLM 调用 */
    private String callLLMSync(String input) throws IOException {
        String json = buildChatBody(input);
        Request request = new Request.Builder()
                .url(apiBaseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();

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

    /** 异步 LLM 调用 */
    private void callLLMAsync(String input, ChatCallback callback) {
        String json = buildChatBody(input);
        Request request = new Request.Builder()
                .url(apiBaseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // LLM 失败，回退规则
                String[] result = ruleChat(input);
                mainHandler.post(() -> callback.onReply(result[0], result[1]));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        fallbackToRule(input, callback);
                        return;
                    }
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
                    fallbackToRule(input, callback);
                } catch (Exception e) {
                    fallbackToRule(input, callback);
                }
            }

            private void fallbackToRule(String input, ChatCallback callback) {
                String[] result = ruleChat(input);
                mainHandler.post(() -> callback.onReply(result[0], result[1]));
            }
        });
    }

    /** 构建 OpenAI 兼容的 chat body */
    private String buildChatBody(String userInput) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray messages = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", "你是语梦，一个温柔可爱的虚拟助手。用中文回复，语气亲切自然，回复简洁（不超过100字）。");
            messages.put(sysMsg);
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userInput);
            messages.put(userMsg);
            body.put("messages", messages);
            body.put("temperature", 0.8);
            body.put("max_tokens", 200);
            return body.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 本地规则引擎（回退） */
    private String[] ruleChat(String input) {
        String reply = inferRuleReply(input);
        String emotion = detectEmotion(input, reply);
        return new String[]{reply, emotion};
    }

    private String inferRuleReply(String input) {
        if (input.contains("你好") || input.contains("嗨") || input.contains("hello")) {
            return "你好呀~ 我是语梦，今天有什么想聊的吗？ 🌙";
        }
        if (input.contains("名字") || input.contains("你是谁")) {
            return "我叫语梦，是一个由 AI 驱动的虚拟助手 ✨";
        }
        if (input.contains("梦") || input.contains("做梦")) {
            return "梦是很奇妙的呢…有人说梦是潜意识的映射 💭";
        }
        if (input.contains("天气")) {
            return "无论晴天还是雨天，和你聊天都是好天气~ ☀️";
        }
        if (input.contains("谢谢") || input.contains("感谢")) {
            return "不客气~ 能帮到你我也很开心 😊";
        }
        if (input.contains("再见") || input.contains("拜拜")) {
            return "下次再聊哦，我会想你的~ 👋";
        }
        return "嗯嗯，我听到了：「" + truncate(input, 20) + "」… 继续说，我在听呢 🌸";
    }

    private String detectEmotion(String input, String reply) {
        String combined = input + " " + reply;
        for (Map.Entry<String, String> entry : EMOTION_MAP.entrySet()) {
            if (combined.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "neutral";
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }
}
