package com.yumeng.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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
 * AI 对话引擎 + 情感联动（Live2D 待集成）
 */
public class MainActivity extends AppCompatActivity {

    private TextView headerView;
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
        headerView = findViewById(R.id.headerView);
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
                // 显示 AI 回复 + 情感提示
                String label = "语梦" + (emotion != null ? " [" + emotion + "]" : "");
                addMessage(label, reply, emotion);
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
