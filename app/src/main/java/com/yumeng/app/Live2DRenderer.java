package com.yumeng.app;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL 2D 卡通人物渲染器
 * 纯代码绘制：头部、眼睛、嘴巴、身体 + 表情动画
 */
public class Live2DRenderer implements GLSurfaceView.Renderer {

    private String currentEmotion = "neutral";
    private boolean motionPlaying = false;
    private long motionStartTime = 0;
    private long startTime;
    private final Random random = new Random();
    private long nextBlinkTime = 2000;
    private boolean blinking = false;
    private long blinkStart = 0;

    private int program;
    private int uMVPMatrixHandle, aPositionHandle, aColorHandle;

    private final float[] mvpMatrix = new float[16];
    private final float[] projMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] tempMatrix = new float[16];

    private static final String VERTEX_SHADER =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 aPosition;" +
        "attribute vec4 aColor;" +
        "varying vec4 vColor;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * aPosition;" +
        "  vColor = aColor;" +
        "}";

    private static final String FRAGMENT_SHADER =
        "precision mediump float;" +
        "varying vec4 vColor;" +
        "void main() {" +
        "  gl_FragColor = vColor;" +
        "}";

    private static final float[] COLOR_SKIN    = {1.00f, 0.90f, 0.80f, 1.0f};
    private static final float[] COLOR_HAIR    = {0.25f, 0.20f, 0.35f, 1.0f};
    private static final float[] COLOR_EYE     = {0.95f, 0.95f, 0.95f, 1.0f};
    private static final float[] COLOR_PUPIL   = {0.20f, 0.15f, 0.40f, 1.0f};
    private static final float[] COLOR_MOUTH   = {0.80f, 0.40f, 0.40f, 1.0f};
    private static final float[] COLOR_BODY    = {0.35f, 0.30f, 0.55f, 1.0f};
    private static final float[] COLOR_CHEEK   = {1.00f, 0.75f, 0.70f, 0.4f};
    private static final float[] COLOR_RIBBON  = {0.90f, 0.30f, 0.50f, 1.0f};
    private static final float[] COLOR_WHITE   = {1.00f, 1.00f, 1.00f, 1.0f};
    private static final float[] COLOR_DARK    = {0.40f, 0.20f, 0.20f, 1.0f};

    public Live2DRenderer() {}

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.10f, 0.10f, 0.18f, 1.0f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        aColorHandle = GLES20.glGetAttribLocation(program, "aColor");
        startTime = System.currentTimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;
        Matrix.orthoM(projMatrix, 0, -ratio, ratio, -1.0f, 1.0f, -1.0f, 1.0f);
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(program);

        long now = System.currentTimeMillis();
        float elapsed = (now - startTime) / 1000f;

        // 呼吸
        float breath = 1.0f + (float) Math.sin(elapsed * 2.5) * 0.02f;

        // 眨眼
        if (!blinking && now > nextBlinkTime) {
            blinking = true;
            blinkStart = now;
        }
        float blinkY = 1.0f;
        if (blinking) {
            long bt = now - blinkStart;
            if (bt < 80) blinkY = 0.1f;
            else if (bt < 160) blinkY = 1.0f;
            else { blinking = false; nextBlinkTime = now + 2000 + random.nextInt(3000); }
        }

        // 点头
        float nodAngle = 0;
        if (motionPlaying) {
            long mt = now - motionStartTime;
            if (mt < 3000) {
                nodAngle = (float) Math.sin(mt * 0.008) * 3.0f * (1.0f - mt / 3000f);
            } else {
                motionPlaying = false;
            }
        }

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0f, -0.05f, 0f);
        Matrix.scaleM(modelMatrix, 0, breath, breath, 1f);
        Matrix.rotateM(modelMatrix, 0, nodAngle, 1f, 0f, 0f);

        // --- 绘制 ---
        drawCircle(0f, 0.15f, 0.28f, COLOR_HAIR);
        drawCircle(0f, 0.18f, 0.25f, COLOR_SKIN);
        drawCircle(-0.14f, 0.10f, 0.06f, COLOR_CHEEK);
        drawCircle(0.14f, 0.10f, 0.06f, COLOR_CHEEK);

        float eyeY = 0.22f;
        drawOval(-0.09f, eyeY, 0.06f, 0.08f * blinkY, COLOR_EYE);
        drawOval(0.09f, eyeY, 0.06f, 0.08f * blinkY, COLOR_EYE);
        drawOval(-0.09f, eyeY, 0.03f, 0.05f * blinkY, COLOR_PUPIL);
        drawOval(0.09f, eyeY, 0.03f, 0.05f * blinkY, COLOR_PUPIL);
        drawCircle(-0.10f, eyeY + 0.02f, 0.012f, COLOR_WHITE);
        drawCircle(0.08f, eyeY + 0.02f, 0.012f, COLOR_WHITE);

        float browY = eyeY + 0.09f;
        if (currentEmotion.equals("sad")) {
            drawLine(-0.11f, browY - 0.02f, -0.05f, browY, 0.014f, COLOR_HAIR);
            drawLine(0.05f, browY, 0.11f, browY - 0.02f, 0.014f, COLOR_HAIR);
        } else if (currentEmotion.equals("angry")) {
            drawLine(-0.11f, browY, -0.05f, browY + 0.03f, 0.014f, COLOR_HAIR);
            drawLine(0.05f, browY + 0.03f, 0.11f, browY, 0.014f, COLOR_HAIR);
        } else {
            drawLine(-0.12f, browY, -0.04f, browY, 0.014f, COLOR_HAIR);
            drawLine(0.04f, browY, 0.12f, browY, 0.014f, COLOR_HAIR);
        }

        float mouthVal = getMouthValue();
        drawMouth(0f, 0.08f, 0.06f, mouthVal, COLOR_MOUTH);

        float bodyTop = -0.15f;
        drawBody(0f, bodyTop, 0.22f, 0.30f, COLOR_BODY);
        drawCircle(0f, bodyTop + 0.02f, 0.23f, COLOR_BODY);

        drawArc(-0.28f, 0.35f, 0.28f, 0.14f, COLOR_HAIR);
        drawArc(0.28f, 0.35f, 0.28f, 0.14f, COLOR_HAIR);

        drawCircle(-0.22f, 0.38f, 0.05f, COLOR_RIBBON);
        drawCircle(0.22f, 0.38f, 0.05f, COLOR_RIBBON);
    }

    // =================== 绘制工具 ===================

    private void drawCircle(float cx, float cy, float r, float[] color) {
        int n = 32;
        int count = n + 2; // center + ring + close
        FloatBuffer vb = allocFloats(count * 3);
        vb.put(cx).put(cy).put(0f);
        for (int i = 0; i <= n; i++) {
            double a = 2.0 * Math.PI * i / n;
            vb.put(cx + (float) Math.cos(a) * r);
            vb.put(cy + (float) Math.sin(a) * r);
            vb.put(0f);
        }
        vb.position(0);
        drawFan(vb, count, color);
    }

    private void drawOval(float cx, float cy, float rx, float ry, float[] color) {
        int n = 32;
        int count = n + 2;
        FloatBuffer vb = allocFloats(count * 3);
        vb.put(cx).put(cy).put(0f);
        for (int i = 0; i <= n; i++) {
            double a = 2.0 * Math.PI * i / n;
            vb.put(cx + (float) Math.cos(a) * rx);
            vb.put(cy + (float) Math.sin(a) * ry);
            vb.put(0f);
        }
        vb.position(0);
        drawFan(vb, count, color);
    }

    private void drawArc(float cx, float cy, float rx, float ry, float[] color) {
        int n = 16;
        int count = n + 2;
        FloatBuffer vb = allocFloats(count * 3);
        vb.put(cx).put(cy).put(0f);
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * (0.5 + (double) i / n);
            vb.put(cx + (float) Math.cos(a) * rx);
            vb.put(cy + (float) Math.sin(a) * ry);
            vb.put(0f);
        }
        vb.position(0);
        drawFan(vb, count, color);
    }

    private void drawFan(FloatBuffer vb, int count, float[] color) {
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vb);
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, computeMVPMatrix(), 0);
        GLES20.glVertexAttrib4fv(aColorHandle, color, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, count);
    }

    private void drawLine(float x1, float y1, float x2, float y2, float w, float[] color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float nx = -dy / len * w, ny = dx / len * w;
        FloatBuffer vb = allocFloats(18);
        vb.put(new float[]{
            x1 + nx, y1 + ny, 0, x1 - nx, y1 - ny, 0, x2 - nx, y2 - ny, 0,
            x1 + nx, y1 + ny, 0, x2 - nx, y2 - ny, 0, x2 + nx, y2 + ny, 0
        });
        vb.position(0);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vb);
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, computeMVPMatrix(), 0);
        GLES20.glVertexAttrib4fv(aColorHandle, color, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    }

    private void drawMouth(float cx, float cy, float r, float openness, float[] color) {
        if (currentEmotion.equals("happy") || openness > 0.6f) {
            int n = 24;
            FloatBuffer vb = allocFloats(n * 3);
            for (int i = 0; i < n; i++) {
                float t = (float) i / (n - 1);
                float angle = (float) (Math.PI * (0.15 + t * 0.7));
                float rr = r * (0.5f + openness * 0.5f);
                vb.put(cx + (float) Math.cos(angle) * rr * 1.5f);
                vb.put(cy - (float) Math.sin(angle) * rr);
                vb.put(0f);
            }
            vb.position(0);
            GLES20.glLineWidth(3f);
            drawStrip(vb, n, color);
        } else if (currentEmotion.equals("sad")) {
            int n = 24;
            FloatBuffer vb = allocFloats(n * 3);
            for (int i = 0; i < n; i++) {
                float t = (float) i / (n - 1);
                float angle = (float) (Math.PI * (1.15 + t * 0.7));
                vb.put(cx + (float) Math.cos(angle) * r * 1.3f);
                vb.put(cy + 0.04f - (float) Math.sin(angle) * r * 0.6f);
                vb.put(0f);
            }
            vb.position(0);
            GLES20.glLineWidth(3f);
            drawStrip(vb, n, color);
        } else if (currentEmotion.equals("surprised")) {
            drawOval(cx, cy, r * 0.4f, r * 0.8f, COLOR_DARK);
        } else {
            drawOval(cx, cy - 0.01f, r * 0.35f, r * openness * 0.5f, color);
        }
    }

    private void drawStrip(FloatBuffer vb, int count, float[] color) {
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vb);
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, computeMVPMatrix(), 0);
        GLES20.glVertexAttrib4fv(aColorHandle, color, 0);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, count);
    }

    private void drawBody(float cx, float cy, float w, float h, float[] color) {
        float halfW = w / 2, topW = halfW * 0.7f;
        FloatBuffer vb = allocFloats(18);
        vb.put(new float[]{
            cx - topW, cy, 0, cx + topW, cy, 0, cx + halfW, cy - h, 0,
            cx - topW, cy, 0, cx + halfW, cy - h, 0, cx - halfW, cy - h, 0
        });
        vb.position(0);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vb);
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, computeMVPMatrix(), 0);
        GLES20.glVertexAttrib4fv(aColorHandle, color, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    }

    // =================== 缓冲区分配 ===================

    private FloatBuffer allocFloats(int count) {
        return ByteBuffer.allocateDirect(count * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    // =================== MVP矩阵 ===================

    private float[] computeMVPMatrix() {
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tempMatrix, 0);
        return mvpMatrix;
    }

    // =================== 着色器 ===================

    private int createProgram(String vsSrc, String fsSrc) {
        int vs = loadShader(GLES20.GL_VERTEX_SHADER, vsSrc);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fsSrc);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        return p;
    }

    private int loadShader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    // =================== 公共接口 ===================

    public void setEmotion(String emotion) {
        this.currentEmotion = emotion;
    }

    public void startRandomMotion() {
        this.motionPlaying = true;
        this.motionStartTime = System.currentTimeMillis();
    }

    private float getMouthValue() {
        switch (currentEmotion) {
            case "happy":     return 0.8f;
            case "sad":       return 0.2f;
            case "surprised": return 1.0f;
            case "angry":     return 0.15f;
            default:          return 0.35f;
        }
    }
}
