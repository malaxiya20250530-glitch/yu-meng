package com.yumeng.app;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

/**
 * Live2D 模型渲染控件
 * 集成 Cubism Java SDK 进行 2D 模型渲染与表情控制
 */
public class Live2DView extends GLSurfaceView {

    private Live2DRenderer renderer;

    public Live2DView(Context context) {
        super(context);
        init();
    }

    public Live2DView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        renderer = new Live2DRenderer(getContext());
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    /** 根据对话情感切换模型表情 */
    public void setEmotion(String emotion) {
        if (renderer != null) {
            renderer.setEmotion(emotion);
        }
    }

    /** 触发随机动作（点头、摇头、眨眼） */
    public void triggerRandomMotion() {
        if (renderer != null) {
            renderer.startRandomMotion();
        }
    }
}
