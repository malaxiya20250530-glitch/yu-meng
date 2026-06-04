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

    // 着色器
    private int program;
    private int uMVPMatrixHandle, aPositionHandle, aColorHandle;

    // 矩阵
    private final float[] mvpMatrix = new float[16];
    private final float[] projMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] tempMatrix = new float[16];

    private int surfaceWidth, surfaceHeight;

    // 顶点着色器
    private static final String VERTEX_SHADER =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 aPosition;" +
        "attribute vec4 aColor;" +
        "varying vec4 vColor;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * aPosition;" +
        "  vColor = aColor;" +
        "}";

    // 片段着色器
    private static final String FRAGMENT_SHADER =
        "precision mediump float;" +
        "varying vec4 vColor;" +
        "void main() {" +
        "  gl_FragColor = vColor;" +
        "}";

    // ========== 颜色常量 ==========
    private static final float[] COLOR_SKIN    = {1.00f, 0.90f, 0.80f, 1.0f};
    private static final float[] COLOR_HAIR    = {0.25f, 0.20f, 0.35f, 1.0f};
    private static final float[] COLOR_EYE     = {0.95f, 0.95f, 0.95f, 1.0f};
    private static final float[] COLOR_PUPIL   = {0.20f, 0.15f, 0.40f, 1.0f};
    private static final float[] COLOR_MOUTH   = {0.80f, 0.40f, 0.40f, 1.0f};
    private static final float[] COLOR_BODY    = {0.35f, 0.30f, 0.55f, 1.0f};
    private static final float[] COLOR_BLUSH   = {1.00f, 0.60f, 0.60f, 0.3f};
    private static final float[] COLOR_CHEEK   = {1.00f, 0.75f, 0.70f, 0.4f};

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
        surfaceWidth = width;
        surfaceHeight = height;
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

        // 呼吸动画
        float breath = 1.0f + (float) Math.sin(elapsed * 2.5) * 0.02f;

        // 眨眼逻辑
        if (!blinking && now > nextBlinkTime) {
            blinking = true;
            blinkStart = now;
        }
        float blinkFactor = 1.0f;
        if (blinking) {
            long bt = now - blinkStart;
            if (bt < 80) blinkFactor = 0.1f;
            else if (bt < 160) blinkFactor = 1.0f;
            else { blinking = false; nextBlinkTime = now + 2000 + random.nextInt(3000); }
        }

        // 点头动画
        float nodAngle = 0;
        if (motionPlaying) {
            long mt = now - motionStartTime;
            if (mt < 3000) {
                nodAngle = (float) Math.sin(mt * 0.008) * 3.0f * (1.0f - mt / 3000f);
            } else {
                motionPlaying = false;
            }
        }

        // 构建变换矩阵
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0f, -0.05f, 0f);
        Matrix.scaleM(modelMatrix, 0, breath, breath, 1f);
        Matrix.rotateM(modelMatrix, 0, nodAngle, 1f, 0f, 0f);

        // 头部
        drawCircle(0f, 0.15f, 0.28f, COLOR_HAIR);                    // 头发底层
        drawCircle(0f, 0.18f, 0.25f, COLOR_SKIN);                     // 脸

        // 腮红
        drawCircle(-0.14f, 0.10f, 0.06f, COLOR_CHEEK);
        drawCircle(0.14f, 0.10f, 0.06f, COLOR_CHEEK);

        // 眼睛
        float eyeScaleY = blinkFactor;
        float eyeY = 0.22f;
        drawOval(-0.09f, eyeY, 0.06f, 0.08f * eyeScaleY, COLOR_EYE);   // 左眼白
        drawOval(0.09f, eyeY, 0.06f, 0.08f * eyeScaleY, COLOR_EYE);    // 右眼白
        drawOval(-0.09f, eyeY, 0.03f, 0.05f * eyeScaleY, COLOR_PUPIL);  // 左瞳孔
        drawOval(0.09f, eyeY, 0.03f, 0.05f * eyeScaleY, COLOR_PUPIL);   // 右瞳孔

        // 瞳孔高光
        drawCircle(-0.10f, eyeY + 0.02f, 0.012f, new float[]{1,1,1,1});
        drawCircle(0.08f, eyeY + 0.02f, 0.012f, new float[]{1,1,1,1});

        // 眉毛
        float browY = eyeY + 0.09f;
        float browAngle = 0;
        if (currentEmotion.equals("sad")) browAngle = -15f;
        else if (currentEmotion.equals("angry")) browAngle = 15f;
        drawLine(-0.12f, browY, -0.04f, browY + browAngle * 0.002f, 0.015f, COLOR_HAIR);
        drawLine(0.04f, browY + browAngle * 0.002f, 0.12f, browY, 0.015f, COLOR_HAIR);

        // 嘴巴
        float mouthY = 0.08f;
        float mouthVal = getMouthValue();
        drawMouth(0f, mouthY, 0.06f, mouthVal, COLOR_MOUTH);

        // 身体
        float bodyTop = -0.15f;
        drawBody(0f, bodyTop, 0.22f, 0.30f, COLOR_BODY);
        drawCircle(0f, bodyTop + 0.02f, 0.23f, COLOR_BODY); // 衣领

        // 头发刘海
        drawArc(-0.28f, 0.35f, 0.30f, 0.15f, COLOR_HAIR);
        drawArc(0.28f, 0.35f, 0.30f, 0.15f, COLOR_HAIR);

        // 蝴蝶结/发饰
        drawCircle(-0.22f, 0.38f, 0.05f, new float[]{0.9f, 0.3f, 0.5f, 1f});
        drawCircle(0.22f, 0.38f, 0.05f, new float[]{0.9f, 0.3f, 0.5f, 1f});
    }

    // =================== 绘制工具 ===================

    private void drawCircle(float cx, float cy, float r, float[] color) {
        int segments = 32;
        FloatBuffer vertices = ByteBuffer.allocateDirect(segments * 3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(cx); vertices.put(cy); vertices.put(0f);
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            vertices.put(cx + (float) Math.cos(angle) * r);
            vertices.put(cy + (float) Math.sin(angle) * r);
            vertices.put(0f);
        }
        vertices.position(0);
        drawFan(vertices, segments + 2, color);
    }

    private void drawOval(float cx, float cy, float rx, float ry, float[] color) {
        int segments = 32;
        FloatBuffer vertices = ByteBuffer.allocateDirect(segments * 3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(cx); vertices.put(cy); vertices.put(0f);
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            vertices.put(cx + (float) Math.cos(angle) * rx);
            vertices.put(cy + (float) Math.sin(angle) * ry);
            vertices.put(0f);
        }
        vertices.position(0);
        drawFan(vertices, segments + 2, color);
    }

    private void drawFan(FloatBuffer vertices, int count, float[] color) {
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices);
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
        FloatBuffer vertices = ByteBuffer.allocateDirect(6 * 3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(new float[]{
            x1 + nx, y1 + ny, 0, x1 - nx, y1 - ny, 0, x2 - nx, y2 - ny, 0,
            x1 + nx, y1 + ny, 0, x2 - nx, y2 - ny, 0, x2 + nx, y2 + ny, 0
        });
        vertices.position(0);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices);
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, computeMVPMatrix(), 0);
        GLES20.glVertexAttrib4fv(aColorHandle, color, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    }

    private void drawMouth(float cx, float cy, float r, float openness, float[] color) {
        if (currentEmotion.equals("happy") || openness > 0.6f) {
            // 微笑弧线
            int segs = 20;
            FloatBuffer vertices = ByteBuffer.allocateDirect(segs * 3 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
            for (int i = 0; i <= segs; i++) {
                float t = (float) i / segs;
                float angle = (float) (Math.PI * (0.15 + t * 0.7));
                float rr = r * (0.5f + openness * 0.5f);
                vertices.put(cx + (float) Math.cos(angle) * rr * 1.5f);
                vertices.put(cy - (float) Math.sin(angle) * rr);
                vertices.put(0f);
            }
            vertices.position(0);
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices);
            GLES20.glEnableVertexAttribArray(aPositionHandle);
            GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, computeMVPMatrix(), 0);
            GLES20.glVertexAttrib4fv(aColorHandle, color, 0);
            GLES20.glLineWidth(3f);
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, segs + 1);
        } else if (currentEmotion.equals("sad")) {
            // 难过弧线（倒弧）
            int segs = 20;
            FloatBuffer vertices = ByteBuffer.allocateDirect(segs * 3 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
            for (int i = 0; i <= segs; i++) {
                float t = (float) i / segs;
                float angle = (float) (Math.PI * (1.15 + t * 0.7));
                vertices.put(cx + (float) Math.cos(angle) * r * 1.3f);
                vertices.put(cy + 0.04f - (float) Math.sin(angle) * r * 0.6f);
                vertices.put(0f);
            }
            vertices.position(0);
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices);
            GLES20.glEnableVertexAttribArray(aPositionHandle);
            GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, computeMVPMatrix(), 0);
            GLES20.glVertexAttrib4fv(aColorHandle, color, 0);
            GLES20.glLineWidth(3f);
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, segs + 1);
        } else if (currentEmotion.equals("surprised")) {
            // 惊讶圆嘴
            drawOval(cx, cy, r * 0.4f, r * 0.8f, new float[]{0.4f, 0.2f, 0.2f, 1f});
        } else {
            // 中性微张
            drawOval(cx, cy - 0.01f, r * 0.35f, r * openness * 0.5f, color);
        }
    }

    private void drawBody(float cx, float cy, float w, float h, float[] color) {
        float halfW = w / 2;
        FloatBuffer vertices = ByteBuffer.allocateDirect(6 * 3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        // 梯形身体
        float topW = halfW * 0.7f;
        vertices.put(new float[]{
            cx - topW, cy, 0, cx + topW, cy, 0, cx + halfW, cy - h, 0,
            cx - topW, cy, 0, cx + halfW, cy - h, 0, cx - halfW, cy - h, 0
        });
        vertices.position(0);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices);
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, computeMVPMatrix(), 0);
        GLES20.glVertexAttrib4fv(aColorHandle, color, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    }

    private void drawArc(float cx, float cy, float rx, float ry, float[] color) {
        int segs = 16;
        FloatBuffer vertices = ByteBuffer.allocateDirect(segs * 3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(cx); vertices.put(cy); vertices.put(0f);
        for (int i = 0; i <= segs; i++) {
            double angle = Math.PI * (0.5 + (double) i / segs);
            vertices.put(cx + (float) Math.cos(angle) * rx);
            vertices.put(cy + (float) Math.sin(angle) * ry);
            vertices.put(0f);
        }
        vertices.position(0);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices);
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, computeMVPMatrix(), 0);
        GLES20.glVertexAttrib4fv(aColorHandle, color, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, segs + 2);
    }

    private float[] computeMVPMatrix() {
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tempMatrix, 0);
        return mvpMatrix;
    }

    // =================== 着色器编译 ===================

    private int createProgram(String vertexSrc, String fragmentSrc) {
        int vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexSrc);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc);
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vs);
        GLES20.glAttachShader(prog, fs);
        GLES20.glLinkProgram(prog);
        return prog;
    }

    private int loadShader(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        return shader;
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
