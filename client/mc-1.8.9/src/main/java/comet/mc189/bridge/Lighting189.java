package comet.mc189.bridge;

import comet.core.bridge.LightingBackend;
import net.minecraft.client.Minecraft;

public final class Lighting189 implements LightingBackend {
    public static final Lighting189 INSTANCE = new Lighting189();
    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public boolean available() {
        return mc.gameSettings instanceof LightingBackend;
    }

    @Override
    public boolean shadersActive() {
        return LightingBackend.Optifine.shadersActive();
    }

    @Override
    public float gamma() {
        return backend().gamma();
    }

    @Override
    public void setGammaOverride(float gamma) {
        backend().setGammaOverride(gamma);
    }

    @Override
    public void removeGammaOverride() {
        backend().removeGammaOverride();
    }

    @Override
    public boolean consumeUserGammaEdit() {
        return backend().consumeUserGammaEdit();
    }

    private LightingBackend backend() {
        return mc.gameSettings instanceof LightingBackend ? (LightingBackend) mc.gameSettings : LightingBackend.NONE;
    }
}
