package com.yumeng.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Canvas绘制二次元角色 → OpenGL纹理渲染
 * 支持表情切换、呼吸、眨眼、点头动画
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
    private int texId = -1;
    private Bitmap charBitmap;
    private Canvas charCanvas;
    private final int TEX_SIZE = 1024;

    // 纹理四边形
    private FloatBuffer quadVerts, quadTexCoords;

    private static final float[] QUAD_VERTS = {
        -1f, -1f, 0f,  1f, -1f, 0f,  -1f,  1f, 0f,
        -1f,  1f, 0f,  1f, -1f, 0f,   1f,  1f, 0f
    };
    private static final float[] QUAD_TEX = {
        0f, 1f,  1f, 1f,  0f, 0f,
        0f, 0f,  1f, 1f,  1f, 0f
    };

    // 着色器
    private int program;
    private int uTexHandle, aPosHandle, aTexHandle;
    private final float[] mvpMatrix = new float[16];

    private static final String VERTEX =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 aPosition;" +
        "attribute vec2 aTexCoord;" +
        "varying vec2 vTexCoord;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * aPosition;" +
        "  vTexCoord = aTexCoord;" +
        "}";

    private static final String FRAGMENT =
        "precision mediump float;" +
        "varying vec2 vTexCoord;" +
        "uniform sampler2D uTexture;" +
        "void main() {" +
        "  gl_FragColor = texture2D(uTexture, vTexCoord);" +
        "}";

    // 颜色
    private static final int C_SKIN    = 0xFFFFE8D0;
    private static final int C_SKIN_S  = 0xFFFFD8C0;
    private static final int C_HAIR    = 0xFF3A2A5C;
    private static final int C_HAIR_L  = 0xFF5A4A7C;
    private static final int C_EYE_W   = 0xFFFFFFFF;
    private static final int C_EYE_B   = 0xFF2A1A4A;
    private static final int C_EYE_H   = 0xFF8A6ACA;
    private static final int C_MOUTH   = 0xFFD86070;
    private static final int C_BODY    = 0xFF4A3A6C;
    private static final int C_BLUSH   = 0x60FF9090;
    private static final int C_RIBBON  = 0xFFF05070;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_LINE    = 0xFF2A1A3A;

    public Live2DRenderer() {}

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.10f, 0.10f, 0.18f, 1.0f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        program = createProgram(VERTEX, FRAGMENT);
        uTexHandle = GLES20.glGetUniformLocation(program, "uTexture");
        aPosHandle = GLES20.glGetAttribLocation(program, "aPosition");
        aTexHandle = GLES20.glGetAttribLocation(program, "aTexCoord");

        quadVerts = makeBuffer(QUAD_VERTS);
        quadTexCoords = makeBuffer(QUAD_TEX);

        // 创建纹理
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        texId = tex[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        charBitmap = Bitmap.createBitmap(TEX_SIZE, TEX_SIZE, Bitmap.Config.ARGB_8888);
        charCanvas = new Canvas(charBitmap);
        startTime = System.currentTimeMillis();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;
        android.opengl.Matrix.orthoM(mvpMatrix, 0, -ratio, ratio, -1f, 1f, -1f, 1f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        long now = System.currentTimeMillis();
        float elapsed = (now - startTime) / 1000f;

        // 呼吸
        float breath = 1.0f + (float) Math.sin(elapsed * 2.5) * 0.015f;

        // 眨眼
        if (!blinking && now > nextBlinkTime) { blinking = true; blinkStart = now; }
        float blinkY = 1.0f;
        if (blinking) {
            long bt = now - blinkStart;
            if (bt < 80) blinkY = 0.05f;
            else if (bt < 160) blinkY = 1.0f;
            else { blinking = false; nextBlinkTime = now + 2000 + random.nextInt(3000); }
        }

        // 点头
        float nodY = 0;
        if (motionPlaying) {
            long mt = now - motionStartTime;
            if (mt < 3000) nodY = (float) Math.sin(mt * 0.01) * 15f * (1f - mt / 3000f);
            else motionPlaying = false;
        }

        // 绘制角色到Bitmap
        drawCharacter(breath, blinkY, nodY);

        // 上传纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, charBitmap, 0);

        // 渲染
        GLES20.glUseProgram(program);
        GLES20.glUniform1i(uTexHandle, 0);
        GLES20.glVertexAttribPointer(aPosHandle, 3, GLES20.GL_FLOAT, false, 0, quadVerts);
        GLES20.glEnableVertexAttribArray(aPosHandle);
        GLES20.glVertexAttribPointer(aTexHandle, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords);
        GLES20.glEnableVertexAttribArray(aTexHandle);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMVPMatrix"), 1, false, mvpMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    }

    // ==================== Canvas 绘制角色 ====================

    private void drawCharacter(float breath, float blink, float nodY) {
        Canvas c = charCanvas;
        c.drawColor(0x00000000, android.graphics.PorterDuff.Mode.CLEAR);

        float cx = TEX_SIZE / 2f;
        float baseY = TEX_SIZE * 0.58f + nodY;
        float s = breath * TEX_SIZE / 900f;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // ── 头发后层 ──
        p.setColor(C_HAIR);
        c.drawOval(new RectF(cx - 180 * s, baseY - 350 * s, cx + 180 * s, baseY + 20 * s), p);
        c.drawRoundRect(new RectF(cx - 190 * s, baseY - 300 * s, cx + 190 * s, baseY - 80 * s), 60 * s, 60 * s, p);

        // ── 脖子 ──
        p.setColor(C_SKIN_S);
        c.drawRoundRect(new RectF(cx - 35 * s, baseY - 20 * s, cx + 35 * s, baseY + 100 * s), 20 * s, 20 * s, p);

        // ── 脸部 ──
        p.setShader(new RadialGradient(cx, baseY - 30 * s, 170 * s, C_SKIN, C_SKIN_S, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(cx - 150 * s, baseY - 300 * s, cx + 150 * s, baseY + 10 * s), p);
        p.setShader(null);

        // ── 身体 ──
        p.setColor(C_BODY);
        Path body = new Path();
        body.moveTo(cx - 80 * s, baseY - 10 * s);
        body.lineTo(cx + 80 * s, baseY - 10 * s);
        body.lineTo(cx + 140 * s, baseY + 400 * s);
        body.lineTo(cx - 140 * s, baseY + 400 * s);
        body.close();
        c.drawPath(body, p);

        // 衣领
        p.setColor(0xFF5A4A8C);
        Path collar = new Path();
        collar.moveTo(cx - 90 * s, baseY - 10 * s);
        collar.lineTo(cx, baseY + 60 * s);
        collar.lineTo(cx + 90 * s, baseY - 10 * s);
        collar.close();
        c.drawPath(collar, p);

        // ── 蝴蝶结 ──
        drawRibbon(c, cx, baseY + 20 * s, 50 * s, p);

        // ── 眼睛 ──
        float eyeY = baseY - 320 * s;
        drawEye(c, cx - 65 * s, eyeY, 55 * s, blink, p);
        drawEye(c, cx + 65 * s, eyeY, 55 * s, blink, p);

        // ── 眉毛 ──
        drawEyebrows(c, cx, eyeY - 55 * s, 75 * s, p);

        // ── 鼻子 ──
        p.setColor(0x30FFB090);
        c.drawOval(new RectF(cx - 10 * s, eyeY + 100 * s, cx + 10 * s, eyeY + 115 * s), p);

        // ── 嘴巴 ──
        drawMouth(c, cx, eyeY + 140 * s, 30 * s, p);

        // ── 腮红 ──
        p.setColor(C_BLUSH);
        c.drawOval(new RectF(cx - 110 * s, eyeY + 70 * s, cx - 50 * s, eyeY + 120 * s), p);
        c.drawOval(new RectF(cx + 50 * s, eyeY + 70 * s, cx + 110 * s, eyeY + 120 * s), p);

        // ── 前发刘海 ──
        p.setColor(C_HAIR);
        drawBangs(c, cx, baseY - 310 * s, 170 * s, 140 * s, p);
        // 侧发
        drawSideHair(c, cx - 160 * s, baseY - 200 * s, 80 * s, 200 * s, true, p);
        drawSideHair(c, cx + 160 * s, baseY - 200 * s, 80 * s, 200 * s, false, p);

        // ── 高光 ──
        p.setColor(0x20FFFFFF);
        c.drawOval(new RectF(cx - 60 * s, baseY - 310 * s, cx + 60 * s, baseY - 260 * s), p);
    }

    private void drawEye(Canvas c, float x, float y, float size, float blink, Paint p) {
        float sy = blink;

        // 眼白
        p.setColor(C_EYE_W);
        c.drawOval(new RectF(x - size, y - size * 0.7f * sy, x + size, y + size * 0.7f * sy), p);

        // 上睫毛阴影
        p.setColor(0x30000000);
        c.drawOval(new RectF(x - size * 0.95f, y - size * 0.6f * sy, x + size * 0.95f, y - size * 0.2f * sy), p);

        // 虹膜
        p.setShader(new RadialGradient(x, y, size * 0.55f, C_EYE_B, 0xFF0A0A2A, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(x - size * 0.5f, y - size * 0.4f * sy, x + size * 0.5f, y + size * 0.4f * sy), p);
        p.setShader(null);

        // 瞳孔
        p.setColor(0xFF000000);
        c.drawOval(new RectF(x - size * 0.22f, y - size * 0.2f * sy, x + size * 0.22f, y + size * 0.2f * sy), p);

        // 高光1
        p.setColor(C_WHITE);
        c.drawOval(new RectF(x + size * 0.1f, y - size * 0.35f * sy, x + size * 0.28f, y - size * 0.18f * sy), p);
        // 高光2
        c.drawCircle(x - size * 0.15f, y + size * 0.05f * sy, size * 0.08f, p);

        // 上眼线
        p.setColor(C_LINE);
        p.setStrokeWidth(size * 0.06f);
        p.setStyle(Paint.Style.STROKE);
        Path lid = new Path();
        lid.moveTo(x - size * 1.05f, y - size * 0.05f);
        lid.quadTo(x, y - size * 0.85f, x + size * 1.05f, y - size * 0.05f);
        c.drawPath(lid, p);

        // 睫毛
        p.setStrokeWidth(size * 0.04f);
        for (int i = -2; i <= 2; i++) {
            float lx = x + i * size * 0.3f;
            float ly = y - size * 0.7f;
            c.drawLine(lx, ly, lx + i * size * 0.1f, ly - size * 0.2f, p);
        }

        p.setStyle(Paint.Style.FILL);
    }

    private void drawEyebrows(Canvas c, float cx, float y, float spread, Paint p) {
        p.setColor(C_HAIR);
        p.setStrokeWidth(spread * 0.08f);
        p.setStyle(Paint.Style.STROKE);

        if (currentEmotion.equals("sad")) {
            Path lb = new Path(); lb.moveTo(cx - spread, y + spread * 0.15f); lb.quadTo(cx - spread * 0.5f, y, cx - spread * 0.15f, y + spread * 0.1f);
            Path rb = new Path(); rb.moveTo(cx + spread * 0.15f, y + spread * 0.1f); rb.quadTo(cx + spread * 0.5f, y, cx + spread, y + spread * 0.15f);
            c.drawPath(lb, p); c.drawPath(rb, p);
        } else if (currentEmotion.equals("angry")) {
            Path lb = new Path(); lb.moveTo(cx - spread, y - spread * 0.15f); lb.quadTo(cx - spread * 0.5f, y + spread * 0.15f, cx - spread * 0.1f, y);
            Path rb = new Path(); rb.moveTo(cx + spread * 0.1f, y); rb.quadTo(cx + spread * 0.5f, y + spread * 0.15f, cx + spread, y - spread * 0.15f);
            c.drawPath(lb, p); c.drawPath(rb, p);
        } else {
            Path lb = new Path(); lb.moveTo(cx - spread, y); lb.quadTo(cx - spread * 0.5f, y - spread * 0.08f, cx - spread * 0.15f, y);
            Path rb = new Path(); rb.moveTo(cx + spread * 0.15f, y); rb.quadTo(cx + spread * 0.5f, y - spread * 0.08f, cx + spread, y);
            c.drawPath(lb, p); c.drawPath(rb, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawMouth(Canvas c, float x, float y, float size, Paint p) {
        if (currentEmotion.equals("happy")) {
            p.setColor(C_MOUTH);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.15f);
            Path mouth = new Path();
            mouth.moveTo(x - size, y);
            mouth.quadTo(x, y + size * 0.8f, x + size, y);
            c.drawPath(mouth, p);
            p.setStyle(Paint.Style.FILL);
        } else if (currentEmotion.equals("sad")) {
            p.setColor(C_MOUTH);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.15f);
            Path mouth = new Path();
            mouth.moveTo(x - size, y + size * 0.5f);
            mouth.quadTo(x, y - size * 0.3f, x + size, y + size * 0.5f);
            c.drawPath(mouth, p);
            p.setStyle(Paint.Style.FILL);
        } else if (currentEmotion.equals("surprised")) {
            p.setColor(0xFF604050);
            c.drawOval(new RectF(x - size * 0.4f, y, x + size * 0.4f, y + size * 1.2f), p);
        } else {
            p.setColor(C_MOUTH);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.12f);
            Path mouth = new Path();
            mouth.moveTo(x - size * 0.5f, y + size * 0.1f);
            mouth.quadTo(x, y + size * 0.3f, x + size * 0.5f, y + size * 0.1f);
            c.drawPath(mouth, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawBangs(Canvas c, float cx, float top, float width, float height, Paint p) {
        Path bangs = new Path();
        bangs.moveTo(cx - width, top + height * 0.3f);
        bangs.quadTo(cx - width * 0.8f, top - height * 0.1f, cx - width * 0.3f, top);
        bangs.quadTo(cx, top - height * 0.15f, cx + width * 0.3f, top);
        bangs.quadTo(cx + width * 0.8f, top - height * 0.1f, cx + width, top + height * 0.3f);
        bangs.lineTo(cx + width, top + height);
        bangs.lineTo(cx - width, top + height);
        bangs.close();
        c.drawPath(bangs, p);
    }

    private void drawSideHair(Canvas c, float x, float y, float w, float h, boolean left, Paint p) {
        Path hair = new Path();
        hair.moveTo(x, y);
        hair.quadTo(x + (left ? -w * 0.3f : w * 0.3f), y + h * 0.5f, x + (left ? -w * 0.1f : w * 0.1f), y + h);
        hair.lineTo(x + (left ? -w * 0.5f : w * 0.5f), y + h);
        hair.lineTo(x + (left ? -w * 0.5f : w * 0.5f), y);
        hair.close();
        c.drawPath(hair, p);
    }

    private void drawRibbon(Canvas c, float x, float y, float s, Paint p) {
        // 中心结
        p.setColor(C_RIBBON);
        c.drawOval(new RectF(x - s * 0.3f, y - s * 0.3f, x + s * 0.3f, y + s * 0.3f), p);
        // 左翼
        Path lw = new Path();
        lw.moveTo(x - s * 0.2f, y);
        lw.quadTo(x - s * 0.8f, y - s * 0.6f, x - s * 1.2f, y - s * 0.1f);
        lw.quadTo(x - s * 0.7f, y + s * 0.2f, x - s * 0.2f, y);
        c.drawPath(lw, p);
        // 右翼
        Path rw = new Path();
        rw.moveTo(x + s * 0.2f, y);
        rw.quadTo(x + s * 0.8f, y - s * 0.6f, x + s * 1.2f, y - s * 0.1f);
        rw.quadTo(x + s * 0.7f, y + s * 0.2f, x + s * 0.2f, y);
        c.drawPath(rw, p);
    }

    // ==================== 工具 ====================

    private FloatBuffer makeBuffer(float[] data) {
        FloatBuffer b = ByteBuffer.allocateDirect(data.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(data).position(0);
        return b;
    }

    private int createProgram(String vs, String fs) {
        int v = loadShader(GLES20.GL_VERTEX_SHADER, vs);
        int f = loadShader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        return p;
    }

    private int loadShader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    // ==================== 公共API ====================

    public void setEmotion(String emotion) { this.currentEmotion = emotion; }

    public void startRandomMotion() {
        this.motionPlaying = true;
        this.motionStartTime = System.currentTimeMillis();
    }
}
