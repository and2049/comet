package comet.core.render;

import comet.core.mod.MotionBlur;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBTextureFloat;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

public final class MotionBlurPass {
    private static final String VERTEX = "#version 120\n"
            + "varying vec2 uv;\n"
            + "void main() { uv = gl_MultiTexCoord0.xy; gl_Position = gl_Vertex; }\n";
    private static final String FRAGMENT = "#version 120\n"
            + "uniform sampler2D frame;\n"
            + "uniform sampler2D history;\n"
            + "uniform mat4 reproject;\n"
            + "uniform float weight;\n"
            + "uniform vec2 resolution;\n"
            + "varying vec2 uv;\n"
            + "void main() {\n"
            + "  vec2 ndc = uv * 2.0 - 1.0;\n"
            + "  float peripheral = mix(0.3, 1.0, smoothstep(0.0, 0.9, length(ndc)));\n"
            + "  vec4 q = reproject * vec4(ndc, 1.0, 1.0);\n"
            + "  vec2 velocity = q.w > 0.1 ? (ndc - q.xy / q.w) * 0.5 * peripheral : vec2(0.0);\n"
            + "  float size = length(velocity);\n"
            + "  velocity *= min(1.0, " + MotionBlur.MAX_BLUR + " / max(size, 0.00001));\n"
            + "  float taps = clamp(ceil(length(velocity * resolution)), 1.0, " + MotionBlur.TAPS + ".0);\n"
            + "  vec2 inset = 0.5 / resolution;\n"
            + "  vec3 sum = vec3(0.0);\n"
            + "  for (int i = 0; i < " + MotionBlur.TAPS + "; i++) {\n"
            + "    if (float(i) >= taps) break;\n"
            + "    float t = (float(i) + 0.5) / taps - 0.5;\n"
            + "    sum += texture2D(frame, clamp(uv + velocity * t, inset, 1.0 - inset)).rgb;\n"
            + "  }\n"
            + "  gl_FragColor = vec4(mix(sum / taps, texture2D(history, uv).rgb, pow(weight, 1.0 / peripheral)), 1.0);\n"
            + "}\n";

    private final FloatBuffer floats = BufferUtils.createFloatBuffer(16);
    private final IntBuffer ints = BufferUtils.createIntBuffer(16);
    private final float[] projection = new float[16];
    private final float[] modelview = new float[16];
    private final float[] previousProjection = new float[16];
    private final float[] previousModelview = new float[16];
    private final BlurVelocity velocity = new BlurVelocity();
    private long captureNanos;
    private long previousCaptureNanos;
    private boolean captured;
    private boolean historyValid;
    private boolean failed;
    private int width;
    private int height;
    private int frameTexture;
    private int historyTexture;
    private int outputTexture;
    private int framebuffer;
    private int program;
    private int algorithm;

    public void capture() {
        capture(System.nanoTime());
    }

    void capture(long now) {
        if (failed || captured) return;
        read(GL11.GL_PROJECTION_MATRIX, projection);
        read(GL11.GL_MODELVIEW_MATRIX, modelview);
        captureNanos = now;
        captured = true;
    }

    public void apply(MotionBlur mod) {
        if (failed || !captured) return;
        captured = false;
        if (!GLContext.getCapabilities().OpenGL20 || !GLContext.getCapabilities().GL_EXT_framebuffer_object
                || !GLContext.getCapabilities().GL_ARB_texture_float) {
            fail("OpenGL 2.0, floating-point textures and framebuffer objects are required");
            return;
        }
        ints.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, ints);
        int x = ints.get(0);
        int y = ints.get(1);
        int viewWidth = ints.get(2);
        int viewHeight = ints.get(3);
        if (viewWidth <= 0 || viewHeight <= 0) return;
        if (viewWidth != width || viewHeight != height) {
            release();
            width = viewWidth;
            height = viewHeight;
        }
        int selected = mod.number(MotionBlur.ALGORITHM);
        double frameMs = (captureNanos - previousCaptureNanos) / 1e6;
        if (selected != algorithm || previousCaptureNanos == 0 || frameMs <= 0 || frameMs > 100) {
            historyValid = false;
            velocity.reset();
        }
        algorithm = selected;
        float[] transform = historyValid
                ? velocity.update(Reprojection.matrix(previousProjection, previousModelview, projection, modelview), frameMs, mod.shutterMs())
                : BlurVelocity.identity();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousFramebuffer = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            frameTexture = texture(frameTexture, GL11.GL_RGB8);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, width, height);
            historyTexture = texture(historyTexture, ARBTextureFloat.GL_RGBA16F_ARB);
            if (!historyValid) GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, width, height);
            outputTexture = texture(outputTexture, ARBTextureFloat.GL_RGBA16F_ARB);
            if (framebuffer == 0) framebuffer = EXTFramebufferObject.glGenFramebuffersEXT();
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, framebuffer);
            EXTFramebufferObject.glFramebufferTexture2DEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, GL11.GL_TEXTURE_2D, outputTexture, 0);
            GL11.glDrawBuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT);
            if (EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT)
                    != EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT) {
                throw new IllegalStateException("Floating-point history framebuffer is incomplete");
            }
            shader();
            GL11.glViewport(0, 0, width, height);
            GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "frame"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "history"), 1);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "resolution"), width, height);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "weight"),
                    historyValid ? MotionBlur.weight(mod.trailStrength(), frameMs) : 0F);
            floats.clear();
            floats.put(transform).flip();
            GL20.glUniformMatrix4(GL20.glGetUniformLocation(program, "reproject"), false, floats);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, frameTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTexture);
            quad();
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, previousFramebuffer);
            GL11.glViewport(x, y, width, height);
            GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, outputTexture);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            quad();
            GL11.glPopMatrix();
            int swap = historyTexture;
            historyTexture = outputTexture;
            outputTexture = swap;
            historyValid = true;
        } catch (RuntimeException error) {
            fail(error.getMessage());
        } finally {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, previousFramebuffer);
            GL20.glUseProgram(previousProgram);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL13.glActiveTexture(previousActiveTexture);
            GL11.glMatrixMode(previousMatrixMode);
        }
        System.arraycopy(projection, 0, previousProjection, 0, 16);
        System.arraycopy(modelview, 0, previousModelview, 0, 16);
        previousCaptureNanos = captureNanos;
        if (GL11.glGetError() != GL11.GL_NO_ERROR) fail("OpenGL error during blur rendering");
        if (failed) release();
    }

    public void release() {
        if (frameTexture != 0) GL11.glDeleteTextures(frameTexture);
        if (historyTexture != 0) GL11.glDeleteTextures(historyTexture);
        if (outputTexture != 0) GL11.glDeleteTextures(outputTexture);
        if (framebuffer != 0) EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffer);
        if (program != 0) GL20.glDeleteProgram(program);
        frameTexture = historyTexture = outputTexture = framebuffer = program = 0;
        historyValid = false;
        captured = false;
        previousCaptureNanos = 0;
        velocity.reset();
    }

    private void shader() {
        if (program != 0) return;
        int vertex = 0;
        int fragment = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
            fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 1024));
            }
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 1024);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    private void fail(String reason) {
        failed = true;
        System.err.println("[Comet] Motion blur disabled: " + reason);
    }

    private int texture(int existing, int format) {
        if (existing != 0) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, existing);
            return existing;
        }
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return id;
    }

    private void quad() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0F, 0F);
        GL11.glVertex2f(-1F, -1F);
        GL11.glTexCoord2f(1F, 0F);
        GL11.glVertex2f(1F, -1F);
        GL11.glTexCoord2f(1F, 1F);
        GL11.glVertex2f(1F, 1F);
        GL11.glTexCoord2f(0F, 1F);
        GL11.glVertex2f(-1F, 1F);
        GL11.glEnd();
    }

    private void read(int matrix, float[] target) {
        floats.clear();
        GL11.glGetFloat(matrix, floats);
        floats.get(target);
    }
}
