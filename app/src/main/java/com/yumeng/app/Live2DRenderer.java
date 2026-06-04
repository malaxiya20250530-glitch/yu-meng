package com.yumeng.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 3D 哪吒角色渲染器
 * Canvas绘制哪吒 → OpenGL纹理 → 3D旋转卡片 + 浮动动画
 */
public class Live2DRenderer implements GLSurfaceView.Renderer {

    private String currentEmotion = "neutral";
    private boolean motionPlaying;
    private long motionStartTime, startTime;
    private final Random rng = new Random();
    private long nextBlink, blinkStart;
    private boolean blinking;
    private float blinkY = 1f;

    // 纹理
    private int texId = -1;
    private Bitmap bitmap;
    private Canvas texCanvas;
    private static final int TEX = 1024;
    private boolean needRedraw = true;

    // 着色器
    private int program;
    private int uMVP, uTex, aPos, aTexC;
    private FloatBuffer quadV, quadT;
    private final float[] mvp = new float[16], proj = new float[16], viewM = new float[16];
    private final float[] model = new float[16], tmp = new float[16];

    private static final float[] QV = {
        -1,1,0, 1,1,0, -1,-1,0, -1,-1,0, 1,1,0, 1,-1,0
    };
    private static final float[] QT = {
        0,0, 1,0, 0,1, 0,1, 1,0, 1,1
    };

    private static final String VS =
        "uniform mat4 uMVP;" +
        "attribute vec4 aPos;" +
        "attribute vec2 aTex;" +
        "varying vec2 vTex;" +
        "void main(){gl_Position=uMVP*aPos;vTex=aTex;}";

    private static final String FS =
        "precision mediump float;" +
        "varying vec2 vTex;" +
        "uniform sampler2D uTex;" +
        "void main(){gl_FragColor=texture2D(uTex,vTex);}";

    // 哪吒配色
    private static final int C_SKIN   = 0xFFFFE4C8;
    private static final int C_SKIN_S = 0xFFFFD0B0;
    private static final int C_HAIR   = 0xFF1A0A0A;
    private static final int C_RED    = 0xFFD43030;
    private static final int C_RED2   = 0xFFB02020;
    private static final int C_GOLD   = 0xFFFFC040;
    private static final int C_GOLD2  = 0xFFE0A020;
    private static final int C_EYEW   = 0xFFFFFFFF;
    private static final int C_EYEB   = 0xFF1A0A0A;
    private static final int C_MOUTH  = 0xFFD06060;
    private static final int C_FLAME  = 0xFFFF6030;
    private static final int C_FLAME2 = 0xFFFFD040;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_BLUSH  = 0x60FF8888;
    private static final int C_BELT   = 0xFF8B4513;

    public Live2DRenderer() {}

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.06f, 0.05f, 0.12f, 1f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        program = createProgram(VS, FS);
        uMVP = GLES20.glGetUniformLocation(program, "uMVP");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aTexC= GLES20.glGetAttribLocation(program, "aTex");

        quadV = makeFB(QV);
        quadT = makeFB(QT);

        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        texId = t[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        bitmap = Bitmap.createBitmap(TEX, TEX, Bitmap.Config.ARGB_8888);
        texCanvas = new Canvas(bitmap);
        drawNezha();
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        startTime = System.currentTimeMillis();
        nextBlink = 2000;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        float r = (float) w / h;
        float s = 0.95f;
        Matrix.orthoM(proj, 0, -r * s, r * s, -s, s, -1f, 1f);
        Matrix.setLookAtM(viewM, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        long now = System.currentTimeMillis();
        float t = (now - startTime) / 1000f;

        // 眨眼
        if (!blinking && now > nextBlink) { blinking = true; blinkStart = now; needRedraw = true; }
        if (blinking) {
            long bt = now - blinkStart;
            blinkY = bt < 60 ? 0.03f : bt < 130 ? 1f : 1f;
            if (bt >= 130) { blinking = false; nextBlink = now + 2000 + rng.nextInt(3000); }
        }

        // 点头
        float nod = 0;
        if (motionPlaying) {
            long mt = now - motionStartTime;
            if (mt < 2500) nod = (float) Math.sin(mt * 0.012) * 4f * (1f - mt / 2500f);
            else motionPlaying = false;
        }

        // 表情变化需要重绘
        if (needRedraw) {
            drawNezha();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            needRedraw = false;
        }

        // 3D旋转矩阵
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, 0f, (float) Math.sin(t * 1.5f) * 0.04f, 0f); // 上下浮动
        Matrix.rotateM(model, 0, (float) Math.sin(t * 0.55f) * 18f, 0f, 1f, 0f); // Y轴旋转
        Matrix.rotateM(model, 0, nod, 1f, 0f, 0f);
        Matrix.scaleM(model, 0, 1f + (float)Math.sin(t*2.3f)*0.012f, 1f + (float)Math.sin(t*2.3f)*0.012f, 1f);
        Matrix.multiplyMM(tmp, 0, viewM, 0, model, 0);
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0);

        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, quadV);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aTexC, 2, GLES20.GL_FLOAT, false, 0, quadT);
        GLES20.glEnableVertexAttribArray(aTexC);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    }

    // ==================== Canvas绘制哪吒 ====================

    private void drawNezha() {
        Canvas c = texCanvas;
        c.drawColor(0x00000000, android.graphics.PorterDuff.Mode.CLEAR);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = TEX / 2f, s = TEX / 950f;

        // ── 披风/混天绫(背景) ──
        p.setColor(C_RED);
        Path cape = new Path();
        cape.moveTo(cx - 200*s, cx + 280*s);
        cape.quadTo(cx - 300*s, cx + 100*s, cx - 180*s, cx - 200*s);
        cape.lineTo(cx + 180*s, cx - 200*s);
        cape.quadTo(cx + 300*s, cx + 100*s, cx + 200*s, cx + 280*s);
        cape.close();
        c.drawPath(cape, p);

        // 混天绫飘带
        p.setColor(C_RED2);
        Path ribbon = new Path();
        ribbon.moveTo(cx - 160*s, cx - 180*s);
        ribbon.quadTo(cx - 280*s, cx - 280*s, cx - 220*s, cx - 350*s);
        ribbon.quadTo(cx - 150*s, cx - 320*s, cx - 140*s, cx - 200*s);
        c.drawPath(ribbon, p);
        Path ribbon2 = new Path();
        ribbon2.moveTo(cx + 160*s, cx - 180*s);
        ribbon2.quadTo(cx + 280*s, cx - 280*s, cx + 220*s, cx - 350*s);
        ribbon2.quadTo(cx + 150*s, cx - 320*s, cx + 140*s, cx - 200*s);
        c.drawPath(ribbon2, p);

        // ── 身体 ──
        p.setShader(new LinearGradient(cx, cx-60*s, cx, cx+300*s, C_RED, C_RED2, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(cx - 100*s, cx + 10*s, cx + 100*s, cx + 320*s), 40*s, 40*s, p);
        p.setShader(null);

        // 腰带
        p.setColor(C_GOLD);
        c.drawRoundRect(new RectF(cx - 110*s, cx + 100*s, cx + 110*s, cx + 130*s), 15*s, 15*s, p);
        p.setColor(C_GOLD2);
        c.drawRoundRect(new RectF(cx - 20*s, cx + 80*s, cx + 20*s, cx + 150*s), 10*s, 10*s, p);

        // 乾坤圈(脖子)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(12*s);
        p.setColor(C_GOLD);
        c.drawOval(new RectF(cx - 60*s, cx - 70*s, cx + 60*s, cx - 20*s), p);
        p.setStyle(Paint.Style.FILL);

        // ── 头部 ──
        p.setShader(new RadialGradient(cx, cx-200*s, 120*s, C_SKIN, C_SKIN_S, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(cx - 110*s, cx - 310*s, cx + 110*s, cx - 80*s), p);
        p.setShader(null);

        // 包子头(双丸子)
        drawBun(c, cx - 70*s, cx - 310*s, 45*s, p);
        drawBun(c, cx + 70*s, cx - 310*s, 45*s, p);

        // 刘海
        p.setColor(C_HAIR);
        Path bangs = new Path();
        bangs.moveTo(cx - 100*s, cx - 260*s);
        bangs.quadTo(cx - 50*s, cx - 300*s, cx, cx - 280*s);
        bangs.quadTo(cx + 50*s, cx - 300*s, cx + 100*s, cx - 260*s);
        bangs.lineTo(cx + 105*s, cx - 230*s);
        bangs.quadTo(cx, cx - 250*s, cx - 105*s, cx - 230*s);
        bangs.close();
        c.drawPath(bangs, p);

        // 火焰标记(额头)
        drawFlame(c, cx, cx - 290*s, 20*s, p);

        // ── 眼睛 ──
        float eyeY = cx - 210*s;
        float eyeS = 40*s;
        drawNezhaEye(c, cx - 40*s, eyeY, eyeS, blinkY, p);
        drawNezhaEye(c, cx + 40*s, eyeY, eyeS, blinkY, p);

        // 眉毛
        p.setColor(C_HAIR);
        p.setStrokeWidth(5*s);
        p.setStyle(Paint.Style.STROKE);
        if (currentEmotion.equals("angry")) {
            c.drawLine(cx - 70*s, eyeY - 20*s, cx - 10*s, eyeY - 40*s, p);
            c.drawLine(cx + 10*s, eyeY - 40*s, cx + 70*s, eyeY - 20*s, p);
        } else if (currentEmotion.equals("sad")) {
            c.drawLine(cx - 70*s, eyeY - 40*s, cx - 10*s, eyeY - 20*s, p);
            c.drawLine(cx + 10*s, eyeY - 20*s, cx + 70*s, eyeY - 40*s, p);
        } else {
            c.drawLine(cx - 70*s, eyeY - 25*s, cx - 10*s, eyeY - 28*s, p);
            c.drawLine(cx + 10*s, eyeY - 28*s, cx + 70*s, eyeY - 25*s, p);
        }
        p.setStyle(Paint.Style.FILL);

        // ── 鼻子 ──
        p.setColor(0x40C09070);
        c.drawOval(new RectF(cx - 8*s, eyeY + 55*s, cx + 8*s, eyeY + 70*s), p);

        // ── 嘴巴 ──
        drawNezhaMouth(c, cx, eyeY + 85*s, 25*s, p);

        // ── 腮红 ──
        p.setColor(C_BLUSH);
        c.drawOval(new RectF(cx - 90*s, eyeY + 20*s, cx - 30*s, eyeY + 70*s), p);
        c.drawOval(new RectF(cx + 30*s, eyeY + 20*s, cx + 90*s, eyeY + 70*s), p);

        // ── 耳环 ──
        p.setColor(C_GOLD);
        c.drawCircle(cx - 115*s, cx - 180*s, 12*s, p);
        c.drawCircle(cx + 115*s, cx - 180*s, 12*s, p);

        // ── 风火轮(底部) ──
        drawWindFireWheel(c, cx - 80*s, cx + 340*s, 50*s, p);
        drawWindFireWheel(c, cx + 80*s, cx + 340*s, 50*s, p);
    }

    private void drawBun(Canvas c, float x, float y, float r, Paint p) {
        p.setColor(C_HAIR);
        c.drawCircle(x, y, r, p);
        p.setColor(0xFF3A2020);
        c.drawCircle(x, y - r*0.2f, r*0.7f, p);
        // 发簪
        p.setColor(C_GOLD);
        c.drawLine(x - r*0.3f, y - r, x + r*0.3f, y - r*1.3f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r*0.12f);
        c.drawCircle(x, y - r*0.6f, r*0.35f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawFlame(Canvas c, float x, float y, float s, Paint p) {
        p.setShader(new LinearGradient(x, y-s*2, x, y+s, C_FLAME, C_FLAME2, Shader.TileMode.CLAMP));
        Path f = new Path();
        f.moveTo(x, y - s*2);
        f.quadTo(x + s, y - s, x + s*0.3f, y);
        f.quadTo(x + s*0.6f, y + s*0.5f, x, y + s);
        f.quadTo(x - s*0.6f, y + s*0.5f, x - s*0.3f, y);
        f.quadTo(x - s, y - s, x, y - s*2);
        c.drawPath(f, p);
        p.setShader(null);
    }

    private void drawNezhaEye(Canvas c, float x, float y, float s, float blink, Paint p) {
        float sy = blink;
        // 眼白
        p.setColor(C_EYEW);
        c.drawOval(new RectF(x - s, y - s*0.7f*sy, x + s, y + s*0.7f*sy), p);
        // 虹膜
        p.setShader(new RadialGradient(x, y, s*0.5f, C_EYEB, 0xFF0A0A30, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(x - s*0.5f, y - s*0.45f*sy, x + s*0.5f, y + s*0.45f*sy), p);
        p.setShader(null);
        // 瞳孔
        p.setColor(0xFF000000);
        c.drawOval(new RectF(x - s*0.22f, y - s*0.2f*sy, x + s*0.22f, y + s*0.2f*sy), p);
        // 高光
        p.setColor(C_WHITE);
        c.drawOval(new RectF(x + s*0.1f, y - s*0.35f*sy, x + s*0.28f, y - s*0.18f*sy), p);
        c.drawCircle(x - s*0.15f, y + s*0.05f*sy, s*0.08f, p);
        // 上眼线(哪吒的凌厉眼神)
        p.setColor(C_HAIR);
        p.setStrokeWidth(s*0.08f);
        p.setStyle(Paint.Style.STROKE);
        Path lid = new Path();
        lid.moveTo(x - s*1.1f, y - s*0.1f);
        lid.quadTo(x, y - s*0.9f, x + s*1.1f, y - s*0.1f);
        c.drawPath(lid, p);
        // 眼尾上挑
        c.drawLine(x + s*0.9f, y - s*0.2f, x + s*1.3f, y - s*0.45f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawNezhaMouth(Canvas c, float x, float y, float s, Paint p) {
        if (currentEmotion.equals("happy")) {
            p.setColor(C_MOUTH);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s*0.15f);
            Path m = new Path();
            m.moveTo(x - s, y);
            m.quadTo(x, y + s*0.7f, x + s, y);
            c.drawPath(m, p);
            p.setStyle(Paint.Style.FILL);
        } else if (currentEmotion.equals("surprised")) {
            p.setColor(0xFF402030);
            c.drawOval(new RectF(x - s*0.4f, y, x + s*0.4f, y + s*1.1f), p);
        } else if (currentEmotion.equals("sad")) {
            p.setColor(C_MOUTH);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s*0.15f);
            Path m = new Path();
            m.moveTo(x - s, y + s*0.4f);
            m.quadTo(x, y - s*0.3f, x + s, y + s*0.4f);
            c.drawPath(m, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            p.setColor(C_MOUTH);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s*0.1f);
            Path m = new Path();
            m.moveTo(x - s*0.4f, y + s*0.05f);
            m.quadTo(x, y + s*0.25f, x + s*0.4f, y + s*0.05f);
            c.drawPath(m, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawWindFireWheel(Canvas c, float x, float y, float r, Paint p) {
        p.setShader(new RadialGradient(x, y, r, C_FLAME, 0x00FF6030, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
        p.setShader(null);
        p.setColor(C_GOLD);
        p.setStrokeWidth(r*0.15f);
        p.setStyle(Paint.Style.STROKE);
        c.drawCircle(x, y, r*0.7f, p);
        // 火焰齿
        for (int i = 0; i < 8; i++) {
            double a = 2*Math.PI*i/8;
            float fx = x + (float)Math.cos(a)*r*0.8f;
            float fy = y + (float)Math.sin(a)*r*0.8f;
            float tx = x + (float)Math.cos(a)*r*1.2f;
            float ty = y + (float)Math.sin(a)*r*1.2f;
            p.setColor(C_FLAME);
            p.setStrokeWidth(r*0.2f);
            c.drawLine(fx, fy, tx, ty, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    // ==================== 工具 ====================

    private FloatBuffer makeFB(float[] d) {
        FloatBuffer b = ByteBuffer.allocateDirect(d.length*4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(d).position(0);
        return b;
    }

    private int createProgram(String vs, String fs) {
        int v = loadShader(GLES20.GL_VERTEX_SHADER, vs);
        int f = loadShader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        return p;
    }

    private int loadShader(int t, String s) {
        int sh = GLES20.glCreateShader(t);
        GLES20.glShaderSource(sh, s); GLES20.glCompileShader(sh);
        return sh;
    }

    // ==================== 公共API ====================

    public void setEmotion(String emotion) {
        if (!emotion.equals(currentEmotion)) {
            currentEmotion = emotion;
            needRedraw = true;
        }
    }

    public void startRandomMotion() {
        motionPlaying = true;
        motionStartTime = System.currentTimeMillis();
    }
}
