package com.yumeng.app;

/** 对话消息数据结构 */
public class ChatMessage {
    public final String sender;
    public final String text;
    public final String emotion;

    public ChatMessage(String sender, String text, String emotion) {
        this.sender = sender;
        this.text = text;
        this.emotion = emotion;
    }
}
