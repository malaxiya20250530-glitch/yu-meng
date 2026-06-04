package com.yumeng.app;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 3D 动漫角色渲染器
 * 球体头部 + 眼睛球 + 头发片 + Phong光照 + 旋转动画
 */
public class Live2DRenderer implements GLSurfaceView.Renderer {

    private String currentEmotion = "neutral";
    private boolean motionPlaying;
    private long motionStartTime, startTime;
    private final Random rng = new Random();
    private long nextBlink, blinkStart;
    private boolean blinking;
    private float blinkFactor = 1f;

    // 着色器
    private int prog3D, progFlat;
    private int uMVPP, uMVPF, uColorF;
    private int aPosP, aNormP, aColorP, aPosF;
    private int uLightDirP, uAmbientP, uDiffuseP, uSpecularP;

    // 矩阵
    private final float[] mvp = new float[16];
    private final float[] proj = new float[16];
    private final float[] view = new float[16];
    private final float[] model = new float[16];
    private final float[] tmp = new float[16];
    private final float[] tmp2 = new float[16];

    // 球体网格
    private FloatBuffer sphereVerts, sphereNorms;
    private ShortBuffer sphereIndices;
    private int sphereIndexCount;
    private static final int SPHERE_STACKS = 16;
    private static final int SPHERE_SLICES = 24;

    // 眼睛网格(小球体)
    private FloatBuffer eyeVerts, eyeNorms;
    private ShortBuffer eyeIndices;
    private int eyeIndexCount;
    private static final int EYE_STACKS = 8;
    private static final int EYE_SLICES = 12;

    private static final String VERT_3D =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 aPosition;" +
        "attribute vec3 aNormal;" +
        "attribute vec3 aColor;" +
        "varying vec3 vNormal;" +
        "varying vec3 vColor;" +
        "varying vec3 vPos;" +
        "void main() {" +
        "  vec4 wp = uMVPMatrix * aPosition;" +
        "  gl_Position = wp;" +
        "  vPos = aPosition.xyz;" +
        "  vNormal = aNormal;" +
        "  vColor = aColor;" +
        "}";

    private static final String FRAG_3D =
        "precision mediump float;" +
        "varying vec3 vNormal;" +
        "varying vec3 vColor;" +
        "varying vec3 vPos;" +
        "uniform vec3 uLightDir;" +
        "uniform float uAmbient;" +
        "uniform float uDiffuse;" +
        "uniform float uSpecular;" +
        "void main() {" +
        "  vec3 n = normalize(vNormal);" +
        "  vec3 l = normalize(uLightDir);" +
        "  float diff = max(dot(n, l), 0.0);" +
        "  vec3 r = reflect(-l, n);" +
        "  vec3 v = normalize(-vPos);" +
        "  float spec = pow(max(dot(r, v), 0.0), 16.0);" +
        "  float lit = uAmbient + uDiffuse * diff + uSpecular * spec;" +
        "  gl_FragColor = vec4(vColor * lit, 1.0);" +
        "}";

    private static final String VERT_FLAT =
        "uniform mat4 uMVPMatrix;" +
        "attribute vec4 aPosition;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * aPosition;" +
        "}";

    private static final String FRAG_FLAT =
        "precision mediump float;" +
        "uniform vec4 uColor;" +
        "void main() {" +
        "  gl_FragColor = uColor;" +
        "}";

    // 颜色
    private static final float[] SKIN  = {1.00f, 0.88f, 0.78f};
    private static final float[] HAIR  = {0.22f, 0.18f, 0.32f};
    private static final float[] HAIR2 = {0.30f, 0.24f, 0.40f};
    private static final float[] EYEW  = {0.97f, 0.97f, 0.97f};
    private static final float[] PUPIL = {0.15f, 0.12f, 0.30f};
    private static final float[] MOUTH = {0.80f, 0.45f, 0.45f};
    private static final float[] BODY  = {0.32f, 0.26f, 0.48f};
    private static final float[] RIBBON = {0.88f, 0.28f, 0.45f};
    private static final float[] CHEEK = {1.00f, 0.70f, 0.68f};

    public Live2DRenderer() {}

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.08f, 0.08f, 0.16f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        prog3D = createProgram(VERT_3D, FRAG_3D);
        uMVPP = GLES20.glGetUniformLocation(prog3D, "uMVPMatrix");
        aPosP  = GLES20.glGetAttribLocation(prog3D, "aPosition");
        aNormP = GLES20.glGetAttribLocation(prog3D, "aNormal");
        aColorP= GLES20.glGetAttribLocation(prog3D, "aColor");
        uLightDirP = GLES20.glGetUniformLocation(prog3D, "uLightDir");
        uAmbientP  = GLES20.glGetUniformLocation(prog3D, "uAmbient");
        uDiffuseP  = GLES20.glGetUniformLocation(prog3D, "uDiffuse");
        uSpecularP = GLES20.glGetUniformLocation(prog3D, "uSpecular");

        progFlat = createProgram(VERT_FLAT, FRAG_FLAT);
        uMVPF  = GLES20.glGetUniformLocation(progFlat, "uMVPMatrix");
        aPosF  = GLES20.glGetAttribLocation(progFlat, "aPosition");
        uColorF= GLES20.glGetUniformLocation(progFlat, "uColor");

        buildSphere(1f, SPHERE_STACKS, SPHERE_SLICES);
        buildSphere(0.3f, EYE_STACKS, EYE_SLICES, true);

        startTime = System.currentTimeMillis();
        nextBlink = 2000;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        float r = (float) w / h;
        Matrix.perspectiveM(proj, 0, 30f, r, 0.1f, 20f);
        Matrix.setLookAtM(view, 0, 0f, 0.15f, 4.5f, 0f, 0.1f, 0f, 0f, 1f, 0f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        long now = System.currentTimeMillis();
        float t = (now - startTime) / 1000f;
        float breath = 1f + (float) Math.sin(t * 2.2f) * 0.015f;

        // 眨眼
        if (!blinking && now > nextBlink) { blinking = true; blinkStart = now; }
        if (blinking) {
            long bt = now - blinkStart;
            blinkFactor = bt < 70 ? 0.02f : bt < 140 ? 1f : 1f;
            if (bt >= 140) { blinking = false; nextBlink = now + 2000 + rng.nextInt(3000); }
        }

        // 点头
        float nod = 0;
        if (motionPlaying) {
            long mt = now - motionStartTime;
            if (mt < 2500) nod = (float) Math.sin(mt * 0.01) * 6f * (1f - mt / 2500f);
            else motionPlaying = false;
        }

        // 模型矩阵: 旋转 + 呼吸 + 点头
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, 0f, -0.1f, 0f);
        Matrix.rotateM(model, 0, (float) Math.sin(t * 0.4f) * 12f, 0f, 1f, 0f); // 缓慢摇头
        Matrix.rotateM(model, 0, nod, 1f, 0f, 0f);
        Matrix.scaleM(model, 0, breath, breath, breath);

        // 计算MVP
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0);
        Matrix.multiplyMM(mvp, 0, proj, 0, tmp, 0);

        GLES20.glUseProgram(prog3D);
        GLES20.glUniform3f(uLightDirP, 0.5f, 0.8f, 0.3f);
        GLES20.glUniform1f(uAmbientP, 0.25f);
        GLES20.glUniform1f(uDiffuseP, 0.65f);
        GLES20.glUniform1f(uSpecularP, 0.15f);

        // ── 头部球体 ──
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, 0f, 0.3f, 0f);
        Matrix.rotateM(model, 0, (float) Math.sin(t * 0.4f) * 12f, 0f, 1f, 0f);
        Matrix.rotateM(model, 0, nod, 1f, 0f, 0f);
        Matrix.scaleM(model, 0, breath, breath, breath);
        Matrix.scaleM(model, 0, 0.55f, 0.60f, 0.50f); // 扁椭圆头
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0);
        Matrix.multiplyMM(tmp2, 0, proj, 0, tmp, 0);
        drawMesh(sphereVerts, sphereNorms, sphereIndices, sphereIndexCount, SKIN, tmp2);

        // ── 头发上半球 ──
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, 0f, 0.35f, 0f);
        Matrix.rotateM(model, 0, (float) Math.sin(t * 0.4f) * 12f, 0f, 1f, 0f);
        Matrix.rotateM(model, 0, nod, 1f, 0f, 0f);
        Matrix.scaleM(model, 0, breath, breath, breath);
        Matrix.scaleM(model, 0, 0.58f, 0.42f, 0.52f);
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0);
        Matrix.multiplyMM(tmp2, 0, proj, 0, tmp, 0);
        drawMesh(sphereVerts, sphereNorms, sphereIndices, sphereIndexCount, HAIR, tmp2);

        // ── 眼睛球体 ──
        float eyeR = 0.10f;
        float eyeZ = -0.42f;
        float eyeY = 0.28f;
        drawEyeBall(-0.16f, eyeY, eyeZ, eyeR, t, nod, breath);
        drawEyeBall( 0.16f, eyeY, eyeZ, eyeR, t, nod, breath);

        // ── 身体圆锥(用flat shader) ──
        GLES20.glUseProgram(progFlat);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, 0f, -0.4f, 0f);
        Matrix.rotateM(model, 0, (float) Math.sin(t * 0.4f) * 8f, 0f, 1f, 0f);
        Matrix.scaleM(model, 0, breath, breath, breath);
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0);
        Matrix.multiplyMM(tmp2, 0, proj, 0, tmp, 0);
        drawCone(tmp2, BODY);

        // ── 嘴巴(flat线条示意) ──
        drawMouth3D(t, nod, breath);

        // ── 蝴蝶结 ──
        drawRibbon3D(t, nod, breath);

        GLES20.glEnable(GLES20.GL_CULL_FACE);
    }

    private void drawEyeBall(float cx, float cy, float cz, float r, float t, float nod, float breath) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, cx, cy, cz);
        Matrix.rotateM(model, 0, (float) Math.sin(t * 0.4f) * 12f, 0f, 1f, 0f);
        Matrix.rotateM(model, 0, nod, 1f, 0f, 0f);
        Matrix.scaleM(model, 0, breath, breath, breath);
        Matrix.scaleM(model, 0, r, r * blinkFactor, r * 0.5f);
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0);
        Matrix.multiplyMM(tmp2, 0, proj, 0, tmp, 0);
        GLES20.glUseProgram(prog3D);
        drawMesh(eyeVerts, eyeNorms, eyeIndices, eyeIndexCount, EYEW, tmp2);

        // 瞳孔
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, cx, cy, cz + r * 0.6f);
        Matrix.rotateM(model, 0, (float) Math.sin(t * 0.4f) * 12f, 0f, 1f, 0f);
        Matrix.rotateM(model, 0, nod, 1f, 0f, 0f);
        Matrix.scaleM(model, 0, breath, breath, breath);
        Matrix.scaleM(model, 0, r * 0.45f, r * 0.45f * blinkFactor, r * 0.25f);
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0);
        Matrix.multiplyMM(tmp2, 0, proj, 0, tmp, 0);
        drawMesh(eyeVerts, eyeNorms, eyeIndices, eyeIndexCount, PUPIL, tmp2);
    }

    private void drawCone(float[] mvpMatrix, float[] color) {
        // 简易梯形身体: 上面窄下面宽的四边形
        float topY = 0f, botY = -0.6f, topW = 0.22f, botW = 0.38f;
        FloatBuffer vb = allocFloats(18 * 3);
        // front
        addQuad(vb, -topW, topY, 0.05f,  topW, topY, 0.05f,  topW, botY, 0.1f,  -topW, botY, 0.1f);
        // back
        addQuad(vb,  topW, topY, -0.05f, -topW, topY, -0.05f, -topW, botY, -0.1f,  topW, botY, -0.1f);
        // left
        addQuad(vb, -topW, topY, -0.05f, -topW, topY, 0.05f, -topW, botY, 0.1f, -topW, botY, -0.1f);
        // right
        addQuad(vb,  topW, topY, 0.05f,  topW, topY, -0.05f, topW, botY, -0.1f,  topW, botY, 0.1f);
        // top
        addQuad(vb, -topW, topY, -0.05f, topW, topY, -0.05f, topW, topY, 0.05f, -topW, topY, 0.05f);
        // bottom
        addQuad(vb, -botW, botY, -0.1f, botW, botY, -0.1f, botW, botY, 0.1f, -botW, botY, 0.1f);
        vb.position(0);

        GLES20.glUniformMatrix4fv(uMVPF, 1, false, mvpMatrix, 0);
        GLES20.glUniform4f(uColorF, color[0], color[1], color[2], 1f);
        GLES20.glVertexAttribPointer(aPosF, 3, GLES20.GL_FLOAT, false, 0, vb);
        GLES20.glEnableVertexAttribArray(aPosF);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    }

    private void drawMouth3D(float t, float nod, float breath) {
        float mouthY = 0.16f, mouthZ = -0.50f;
        GLES20.glUseProgram(progFlat);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, 0f, mouthY, mouthZ);
        Matrix.rotateM(model, 0, (float) Math.sin(t * 0.4f) * 12f, 0f, 1f, 0f);
        Matrix.rotateM(model, 0, nod, 1f, 0f, 0f);
        Matrix.scaleM(model, 0, breath, breath, breath);
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0);
        Matrix.multiplyMM(tmp2, 0, proj, 0, tmp, 0);

        if (currentEmotion.equals("happy")) {
            drawArc3D(tmp2, 0.06f, 0.04f, MOUTH);
        } else if (currentEmotion.equals("surprised")) {
            FloatBuffer vb = allocFloats(6 * 3);
            addQuad(vb, -0.03f, 0.06f, 0, 0.03f, 0.06f, 0, 0.03f, -0.06f, 0, -0.03f, -0.06f, 0);
            vb.position(0);
            GLES20.glUniformMatrix4fv(uMVPF, 1, false, tmp2, 0);
            GLES20.glUniform4f(uColorF, 0.3f, 0.15f, 0.15f, 1f);
            GLES20.glVertexAttribPointer(aPosF, 3, GLES20.GL_FLOAT, false, 0, vb);
            GLES20.glEnableVertexAttribArray(aPosF);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        } else if (currentEmotion.equals("sad")) {
            drawArc3D(tmp2, -0.04f, 0.04f, MOUTH);
        } else {
            FloatBuffer vb = allocFloats(6 * 3);
            addQuad(vb, -0.04f, 0.01f, 0, 0.04f, 0.01f, 0, 0.04f, -0.01f, 0, -0.04f, -0.01f, 0);
            vb.position(0);
            GLES20.glUniformMatrix4fv(uMVPF, 1, false, tmp2, 0);
            GLES20.glUniform4f(uColorF, MOUTH[0], MOUTH[1], MOUTH[2], 1f);
            GLES20.glVertexAttribPointer(aPosF, 3, GLES20.GL_FLOAT, false, 0, vb);
            GLES20.glEnableVertexAttribArray(aPosF);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        }
        GLES20.glEnable(GLES20.GL_CULL_FACE);
    }

    private void drawArc3D(float[] mvpMatrix, float arcY, float size, float[] color) {
        int n = 24;
        FloatBuffer vb = allocFloats(n * 3);
        for (int i = 0; i < n; i++) {
            float a = (float) (Math.PI * 0.2 + Math.PI * 0.6 * i / (n - 1));
            vb.put((float) Math.cos(a) * size * 1.4f);
            vb.put(arcY + (float) Math.sin(a) * size);
            vb.put(0f);
        }
        vb.position(0);
        GLES20.glUniformMatrix4fv(uMVPF, 1, false, mvpMatrix, 0);
        GLES20.glUniform4f(uColorF, color[0], color[1], color[2], 1f);
        GLES20.glVertexAttribPointer(aPosF, 3, GLES20.GL_FLOAT, false, 0, vb);
        GLES20.glEnableVertexAttribArray(aPosF);
        GLES20.glLineWidth(3f);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, n);
    }

    private void drawRibbon3D(float t, float nod, float breath) {
        GLES20.glUseProgram(progFlat);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        float y = 0.55f, z = -0.35f;
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, 0f, y, z);
        Matrix.rotateM(model, 0, (float) Math.sin(t * 0.4f) * 12f, 0f, 1f, 0f);
        Matrix.rotateM(model, 0, nod, 1f, 0f, 0f);
        Matrix.scaleM(model, 0, breath, breath, breath);
        Matrix.multiplyMM(tmp, 0, view, 0, model, 0);
        Matrix.multiplyMM(tmp2, 0, proj, 0, tmp, 0);

        FloatBuffer vb = allocFloats(12 * 3);
        // 左翼
        addTri(vb, -0.02f, 0, 0, -0.12f, 0.08f, 0, -0.12f, -0.04f, 0);
        addTri(vb, -0.02f, 0, 0, -0.12f, -0.04f, 0, -0.02f, -0.06f, 0);
        // 右翼
        addTri(vb,  0.02f, 0, 0,  0.12f, 0.08f, 0,  0.12f, -0.04f, 0);
        addTri(vb,  0.02f, 0, 0,  0.12f, -0.04f, 0,  0.02f, -0.06f, 0);
        vb.position(0);
        GLES20.glUniformMatrix4fv(uMVPF, 1, false, tmp2, 0);
        GLES20.glUniform4f(uColorF, RIBBON[0], RIBBON[1], RIBBON[2], 1f);
        GLES20.glVertexAttribPointer(aPosF, 3, GLES20.GL_FLOAT, false, 0, vb);
        GLES20.glEnableVertexAttribArray(aPosF);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 12);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
    }

    // ==================== 网格工具 ====================

    private void buildSphere(float r, int stacks, int slices) {
        buildSphere(r, stacks, slices, false);
    }

    private void buildSphere(float r, int stacks, int slices, boolean forEye) {
        int vertCount = (stacks + 1) * (slices + 1);
        float[] verts = new float[vertCount * 3];
        float[] norms = new float[vertCount * 3];
        int idx = 0;
        for (int i = 0; i <= stacks; i++) {
            float phi = (float) (Math.PI * i / stacks);
            for (int j = 0; j <= slices; j++) {
                float theta = (float) (2 * Math.PI * j / slices);
                float x = (float) (Math.sin(phi) * Math.cos(theta));
                float y = (float) Math.cos(phi);
                float z = (float) (Math.sin(phi) * Math.sin(theta));
                verts[idx] = x * r; norms[idx] = x; idx++;
                verts[idx] = y * r; norms[idx] = y; idx++;
                verts[idx] = z * r; norms[idx] = z; idx++;
            }
        }
        int triCount = stacks * slices * 6;
        short[] indices = new short[triCount];
        int ii = 0;
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                short a = (short) (i * (slices + 1) + j);
                short b = (short) (a + slices + 1);
                indices[ii++] = a; indices[ii++] = b; indices[ii++] = (short) (a + 1);
                indices[ii++] = (short) (a + 1); indices[ii++] = b; indices[ii++] = (short) (b + 1);
            }
        }
        if (forEye) {
            eyeVerts = makeFloatBuffer(verts);
            eyeNorms = makeFloatBuffer(norms);
            eyeIndices = makeShortBuffer(indices);
            eyeIndexCount = triCount;
        } else {
            sphereVerts = makeFloatBuffer(verts);
            sphereNorms = makeFloatBuffer(norms);
            sphereIndices = makeShortBuffer(indices);
            sphereIndexCount = triCount;
        }
    }

    private void drawMesh(FloatBuffer verts, FloatBuffer norms, ShortBuffer indices, int count, float[] color, float[] mvpMat) {
        GLES20.glUniformMatrix4fv(uMVPP, 1, false, mvpMat, 0);
        GLES20.glVertexAttribPointer(aPosP, 3, GLES20.GL_FLOAT, false, 0, verts);
        GLES20.glEnableVertexAttribArray(aPosP);
        GLES20.glVertexAttribPointer(aNormP, 3, GLES20.GL_FLOAT, false, 0, norms);
        GLES20.glEnableVertexAttribArray(aNormP);
        GLES20.glVertexAttrib3f(aColorP, color[0], color[1], color[2]);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, indices);
    }

    private void addQuad(FloatBuffer b, float x1,float y1,float z1, float x2,float y2,float z2,
                          float x3,float y3,float z3, float x4,float y4,float z4) {
        b.put(new float[]{x1,y1,z1, x2,y2,z2, x3,y3,z3, x1,y1,z1, x3,y3,z3, x4,y4,z4});
    }

    private void addTri(FloatBuffer b, float x1,float y1,float z1, float x2,float y2,float z2,
                         float x3,float y3,float z3) {
        b.put(new float[]{x1,y1,z1, x2,y2,z2, x3,y3,z3});
    }

    // ==================== 缓冲区 ====================

    private FloatBuffer allocFloats(int n) {
        return ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private FloatBuffer makeFloatBuffer(float[] data) {
        FloatBuffer b = allocFloats(data.length);
        b.put(data).position(0);
        return b;
    }

    private ShortBuffer makeShortBuffer(short[] data) {
        ShortBuffer b = ByteBuffer.allocateDirect(data.length * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer();
        b.put(data).position(0);
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

    private int loadShader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s);
        return s;
    }

    // ==================== 公共API ====================

    public void setEmotion(String emotion) { this.currentEmotion = emotion; }
    public void startRandomMotion() {
        this.motionPlaying = true;
        this.motionStartTime = System.currentTimeMillis();
    }
}
