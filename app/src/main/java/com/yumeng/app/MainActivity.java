package com.yumeng.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.yumeng.plugin.CalculatorPlugin;
import com.yumeng.plugin.PluginManager;
import com.yumeng.plugin.PluginManifest;
import com.yumeng.plugin.SystemPlugin;
import com.yumeng.plugin.WeatherPlugin;
import java.util.ArrayList;
import java.util.List;

/**
 * 语梦主界面
 * Live2D 渲染 + LLM + 插件路由
 */
public class MainActivity extends AppCompatActivity {

    private Live2DView live2dView;
    private RecyclerView chatArea;
    private EditText inputText;
    private Button sendBtn;

    private OperitEngine aiEngine;
    private PluginManager pluginManager;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isWaitingReply = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        live2dView = findViewById(R.id.live2dView);
        chatArea = findViewById(R.id.chatArea);
        inputText = findViewById(R.id.inputText);
        sendBtn = findViewById(R.id.sendBtn);

        // 初始化插件系统
        pluginManager = new PluginManager(this);
        pluginManager
            .install(new WeatherPlugin(), PluginManifest.create("weather", "WeatherPlugin").description("天气查询"))
            .install(new SystemPlugin(), PluginManifest.create("system", "SystemPlugin").description("系统信息/时间"))
            .install(new CalculatorPlugin(), PluginManifest.create("calculator", "CalculatorPlugin").description("四则运算"));

        // 初始化引擎，注入插件管理器
        aiEngine = new OperitEngine();
        aiEngine.setPluginManager(pluginManager);

        // 对话列表
        chatAdapter = new ChatAdapter(messages);
        chatArea.setLayoutManager(new LinearLayoutManager(this));
        chatArea.setAdapter(chatAdapter);

        // 欢迎语
        addMessage("语梦", "你好~ 我是语梦 🌙\nLLM已接入 · 插件系统已就绪\n可以问我天气、时间、计算哦", "happy");
        live2dView.setEmotion("happy");
        live2dView.triggerRandomMotion();

        sendBtn.setOnClickListener(v -> onSendMessage());
    }

    private void onSendMessage() {
        if (isWaitingReply) return;
        String text = inputText.getText().toString().trim();
        if (text.isEmpty()) return;

        addMessage("你", text, null);
        inputText.setText("");
        isWaitingReply = true;

        aiEngine.chatAsync(text, new OperitEngine.ChatCallback() {
            @Override
            public void onReply(String reply, String emotion) {
                mainHandler.post(() -> {
                    addMessage("语梦", reply, emotion);
                    live2dView.setEmotion(emotion != null ? emotion : "neutral");
                    live2dView.triggerRandomMotion();
                    isWaitingReply = false;
                });
            }
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    addMessage("语梦", "唔…出错了呢 🌸", "sad");
                    live2dView.setEmotion("sad");
                    isWaitingReply = false;
                });
            }
        });
    }

    private void addMessage(String sender, String text, String emotion) {
        messages.add(new ChatMessage(sender, text, emotion));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        chatArea.scrollToPosition(messages.size() - 1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
