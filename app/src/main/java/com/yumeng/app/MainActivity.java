package com.yumeng.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isWaitingReply = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化视图
        live2dView = findViewById(R.id.live2dView);
        chatArea = findViewById(R.id.chatArea);
        inputText = findViewById(R.id.inputText);
        sendBtn = findViewById(R.id.sendBtn);

        // 初始化 AI 引擎（默认连接本地 Ollama，失败自动回退规则引擎）
        aiEngine = new OperitEngine(this);

        // 对话列表
        chatAdapter = new ChatAdapter(messages);
        chatArea.setLayoutManager(new LinearLayoutManager(this));
        chatArea.setAdapter(chatAdapter);

        // 欢迎语 + Live2D 联动
        addMessage("语梦", "你好~ 我是语梦 🌙 LLM已接入，想聊什么都可以哦", "happy");
        live2dView.setEmotion("happy");
        live2dView.triggerRandomMotion();

        // 发送按钮
        sendBtn.setOnClickListener(v -> onSendMessage());
    }

    private void onSendMessage() {
        if (isWaitingReply) return;
        String text = inputText.getText().toString().trim();
        if (text.isEmpty()) return;

        // 显示用户消息
        addMessage("你", text, null);
        inputText.setText("");
        isWaitingReply = true;

        // 异步 LLM 调用（自动回退规则引擎）
        aiEngine.chatAsync(text, new OperitEngine.ChatCallback() {
            @Override
            public void onReply(String reply, String emotion) {
                mainHandler.post(() -> {
                    String label = "语梦";
                    addMessage(label, reply, emotion);
                    // 联动 Live2D 表情 + 动作
                    String em = emotion != null ? emotion : "neutral";
                    live2dView.setEmotion(em);
                    live2dView.triggerRandomMotion();
                    isWaitingReply = false;
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    addMessage("语梦", "唔…网络好像有点问题，等等再试？ 🌸", "sad");
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
