package comet.mc189.render;

import comet.core.anim.Transform;
import net.minecraft.client.renderer.GlStateManager;

public final class GlTransform implements Transform {
    public static final GlTransform INSTANCE = new GlTransform();

    private GlTransform() {
    }

    @Override
    public void translate(float x, float y, float z) {
        GlStateManager.translate(x, y, z);
    }

    @Override
    public void rotate(float angle, float x, float y, float z) {
        GlStateManager.rotate(angle, x, y, z);
    }

    @Override
    public void scale(float x, float y, float z) {
        GlStateManager.scale(x, y, z);
    }
}
