package com.yumeng.app;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Live2D OpenGL 渲染器
 * 负责加载模型纹理、应用表情参数、处理动画循环
 */
public class Live2DRenderer implements GLSurfaceView.Renderer {

    private final Context context;
    private String currentEmotion = "neutral";
    private boolean motionPlaying = false;
    private long motionStartTime = 0;

    public Live2DRenderer(Context context) {
        this.context = context;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        // 加载 Live2D 模型资源 (assets/models/)
        loadModel("yumeng.model3.json");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        // 更新 Live2D 模型参数
        updateModel();
        // 渲染模型
        drawModel();
    }

    private void loadModel(String modelPath) {
        // Live2D Cubism SDK: 从 assets 加载 .model3.json
        // cubismModel = CubismUserModel.loadModel(modelPath);
    }

    private void updateModel() {
        // 应用当前表情参数
        // cubismModel.setParameterValue("ParamMouthOpen", getMouthValue());
        // 处理动作动画
        if (motionPlaying) {
            updateMotion();
        }
    }

    private void drawModel() {
        // CubismRenderer.drawModel()
    }

    public void setEmotion(String emotion) {
        this.currentEmotion = emotion;
    }

    public void startRandomMotion() {
        this.motionPlaying = true;
        this.motionStartTime = System.currentTimeMillis();
    }

    private void updateMotion() {
        long elapsed = System.currentTimeMillis() - motionStartTime;
        if (elapsed > 3000) {
            motionPlaying = false;
        }
    }

    private float getMouthValue() {
        switch (currentEmotion) {
            case "happy":  return 0.8f;
            case "sad":    return 0.2f;
            case "surprised": return 1.0f;
            default:       return 0.5f;
        }
    }
}
