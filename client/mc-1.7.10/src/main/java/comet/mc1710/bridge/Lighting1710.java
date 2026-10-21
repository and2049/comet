package comet.mc1710.bridge;

import comet.core.bridge.LightingBackend;
import net.minecraft.client.Minecraft;

public final class Lighting1710 implements LightingBackend {
    public static final Lighting1710 INSTANCE = new Lighting1710();
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
