package com.yumeng.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 语梦主界面
 * Live2D 模型展示 + Operit AI 对话引擎 + 情感联动
 */
public class MainActivity extends AppCompatActivity {

    private Live2DView live2dView;
    private RecyclerView chatArea;
    private EditText inputText;
    private Button sendBtn;

    private OperitEngine aiEngine;
    private ChatAdapter chatAdapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化视图
        live2dView = findViewById(R.id.live2dView);
        chatArea = findViewById(R.id.chatArea);
        inputText = findViewById(R.id.inputText);
        sendBtn = findViewById(R.id.sendBtn);

        // 初始化 AI 引擎
        aiEngine = new OperitEngine(this);
        aiEngine.load();

        // 对话列表
        chatAdapter = new ChatAdapter(messages);
        chatArea.setLayoutManager(new LinearLayoutManager(this));
        chatArea.setAdapter(chatAdapter);

        // 欢迎语
        addMessage("语梦", "你好~ 我是语梦 🌙 想聊什么都可以哦", "happy");
        live2dView.setEmotion("happy");
        live2dView.triggerRandomMotion();

        // 发送按钮
        sendBtn.setOnClickListener(v -> onSendMessage());
    }

    private void onSendMessage() {
        String text = inputText.getText().toString().trim();
        if (text.isEmpty()) return;

        // 显示用户消息
        addMessage("你", text, null);
        inputText.setText("");

        // 异步推理
        executor.execute(() -> {
            String[] result = aiEngine.chat(text);
            String reply = result[0];
            String emotion = result[1];

            mainHandler.post(() -> {
                // 显示 AI 回复
                addMessage("语梦", reply, emotion);
                // 联动 Live2D 表情
                live2dView.setEmotion(emotion != null ? emotion : "neutral");
                live2dView.triggerRandomMotion();
            });
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
        executor.shutdown();
    }
}
