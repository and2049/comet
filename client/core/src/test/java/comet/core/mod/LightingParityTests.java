package comet.core.mod;

import comet.core.bridge.LightingBackend;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class LightingParityTests {
    private static int assertions;

    private LightingParityTests() {
    }

    public static void run(Path root) {
        try {
            Files.createDirectories(root);
            requests(root.resolve("requests"));
            vanillaEdit(root.resolve("edit"));
            saveAndLoad(root.resolve("persist"));
            lifecycle(root.resolve("lifecycle"));
            binding(root.resolve("binding"));
            fractional(root.resolve("fractional"));
            shaders(root.resolve("shaders"));
            failedWrite(root.resolve("failed"));
            compatibility(root.resolve("compatibility"));
            System.out.println("Comet lighting parity: " + assertions + " assertions passed");
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Lighting parity setup failed", failure);
        }
    }

    private static void requests(Path root) {
        for (float original : new float[] {0F, 0.3F, 1F}) {
            FakeBackend backend = new FakeBackend();
            backend.preference = original;
            Lighting lighting = new Lighting(backend);
            registry(root, lighting);
            lighting.setNumber(Lighting.METHOD, Lighting.GAMMA);
            lighting.setOption(Lighting.FULLBRIGHT, true);
            lighting.tick();
            check(backend.requested == 100F, "Fullbright requests gamma 100 with an original gamma of " + original);
            check(backend.gamma() == 100F, "The active gamma is the fullbright override");
            lighting.setOption(Lighting.FULLBRIGHT, false);
            for (float boost : new float[] {1F, 2.5F, 10F}) {
                lighting.setNumber(Lighting.BOOST, Math.round(boost * 100F));
                lighting.tick();
                check(backend.requested == boost, "The gamma boost requests " + boost + " exactly");
            }
            check(backend.gamma() == 10F, "The last boost stays active");
            check(backend.captures == 1, "The original gamma is captured once");
        }
    }

    private static void vanillaEdit(Path root) {
        FakeBackend backend = new FakeBackend();
        Lighting lighting = new Lighting(backend);
        ModRegistry registry = registry(root, lighting);
        lighting.setNumber(Lighting.METHOD, Lighting.GAMMA);
        lighting.setOption(Lighting.FULLBRIGHT, true);
        registry.tick();
        check(backend.overridden() && backend.gamma() == 100F, "The fullbright override is active");
        backend.editGamma(0.75F);
        registry.tick();
        check(!backend.overridden(), "A vanilla gamma change releases the override");
        check(backend.gamma() == 0.75F, "The edited vanilla preference is respected");
        check(!registry.isEnabled(lighting), "A vanilla gamma change disables Lighting rather than applying lightmap fullbright");
        check(lighting.status().contains("gamma"), "The automatic disable is reported");
        check(backend.captures == 1, "The released override is not recaptured");
        registry.toggle(lighting);
        registry.tick();
        check(backend.gamma() == 100F, "Re-enabling Lighting overrides the edited preference");
        check(backend.captures == 2, "The edited preference becomes the new baseline");
        registry.reset();
        check(backend.gamma() == 0.75F, "Releasing restores the edited vanilla preference");
    }

    private static void saveAndLoad(Path root) {
        FakeBackend backend = new FakeBackend();
        Lighting lighting = new Lighting(backend);
        registry(root, lighting);
        lighting.setNumber(Lighting.METHOD, Lighting.GAMMA);
        lighting.setOption(Lighting.FULLBRIGHT, true);
        lighting.tick();
        backend.save();
        check(backend.persisted == 0.3F, "The gamma override is never written to the options file");
        backend.load();
        check(backend.gamma() == 100F, "Loading options keeps the active override");
        check(backend.saved == 0.3F, "The loaded preference stays under the override");
        backend.editGamma(0.6F);
        backend.save();
        check(backend.persisted == 0.6F, "A vanilla gamma edit is written instead of the override");
        backend.load();
        lighting.reset();
        check(backend.gamma() == 0.6F, "The saved preference survives a release after loading");
        check(backend.persisted != 100F, "100 is never persisted");
    }

    private static void lifecycle(Path root) {
        FakeBackend backend = new FakeBackend();
        Lighting lighting = new Lighting(backend);
        ModRegistry registry = registry(root, lighting);
        lighting.setNumber(Lighting.METHOD, Lighting.GAMMA);
        lighting.setOption(Lighting.FULLBRIGHT, true);
        registry.tick();
        check(backend.overridden(), "Gamma brightness overrides the gamma");
        registry.toggle(lighting);
        check(!registry.isEnabled(lighting) && !backend.overridden(), "Disabling Lighting releases the override");
        float released = backend.gamma();
        registry.tick();
        check(backend.gamma() == released && !backend.overridden(), "A disabled tick never changes gamma");
        registry.toggle(lighting);
        registry.tick();
        check(backend.overridden(), "Re-enabling restores the override");
        registry.reset();
        check(!backend.overridden(), "World exit releases the override");
        registry.tick();
        check(backend.overridden(), "A later tick reapplies the override");
        lighting.setNumber(Lighting.METHOD, Lighting.LIGHTMAP);
        registry.tick();
        check(!backend.overridden(), "Switching back to the lightmap method releases the override");
        lighting.setNumber(Lighting.METHOD, Lighting.GAMMA);
        registry.tick();
        check(backend.overridden(), "Switching to gamma reapplies the override");
        registry.settings().savePreset();
        registry.loadPreset("Default");
        check(!backend.overridden(), "Preset switching releases the override");
        check(registry.isEnabled(lighting), "The saved preset keeps Lighting enabled");
    }

    private static void binding(Path root) {
        FakeBackend backend = new FakeBackend();
        Lighting lighting = new Lighting(backend);
        ModRegistry registry = registry(root, lighting);
        lighting.setNumber(Lighting.METHOD, Lighting.GAMMA);
        lighting.setOption(Lighting.FULLBRIGHT, false);
        lighting.setNumber(Lighting.TOGGLE, 34);
        check(lighting.number(Lighting.TOGGLE) == 34, "The fullbright binding accepts a key code");
        registry.keyState(0, true);
        check(!lighting.option(Lighting.FULLBRIGHT), "An unbound key never toggles fullbright");
        registry.keyState(34, true);
        check(lighting.option(Lighting.FULLBRIGHT), "The fullbright binding toggles the option");
        check(backend.gamma() == 100F, "Toggling fullbright applies the override immediately");
        registry.keyState(34, true);
        registry.keyState(34, true);
        check(lighting.option(Lighting.FULLBRIGHT), "Repeated key-down events do not repeat the toggle");
        registry.keyState(34, false);
        registry.keyState(34, true);
        check(!lighting.option(Lighting.FULLBRIGHT), "A fresh press toggles back");
        check(backend.gamma() == 1F, "Toggling fullbright off applies the gamma boost");
        registry.reset();
        check(!backend.overridden(), "World exit releases the override");
    }

    private static void fractional(Path root) {
        FakeBackend backend = new FakeBackend();
        Lighting lighting = new Lighting(backend);
        registry(root, lighting);
        check(Lighting.BOOST.defaultValue == 100 && Lighting.BOOST.min == 100 && Lighting.BOOST.max == 1000,
                "The gamma boost persists as a scaled int with two decimals");
        check(close(lighting.decimal(Lighting.BOOST), 1.0), "The gamma boost defaults to 1x");
        lighting.setNumber(Lighting.BOOST, 250);
        check(close(lighting.decimal(Lighting.BOOST), 2.5), "A scaled int reads back as 2.5");
        check(lighting.number(Lighting.BOOST) == 250, "The raw gamma boost stays a scaled int");
        lighting.setNumber(Lighting.BOOST, 99999);
        check(lighting.number(Lighting.BOOST) == 1000, "The gamma boost is bounded to 10");
        ModSettings restarted = new ModSettings(root.toFile());
        check(restarted.number("lighting", "brightnessBoost") == 1000, "A fractional boost survives a restart");
        Lighting reloaded = new Lighting(new FakeBackend());
        reloaded.attach(restarted);
        check(close(reloaded.decimal(Lighting.BOOST), 10.0), "The restarted boost reads back as 10");
    }

    private static void shaders(Path root) {
        FakeBackend backend = new FakeBackend();
        backend.shaders = true;
        Lighting lighting = new Lighting(backend);
        registry(root, lighting);
        lighting.setNumber(Lighting.METHOD, Lighting.GAMMA);
        lighting.setOption(Lighting.FULLBRIGHT, true);
        lighting.tick();
        check(!backend.overridden(), "An active shader pack is neither overridden nor disabled");
        check(backend.gamma() == 0.3F, "Shaders keep the vanilla gamma preference");
        check(lighting.status().contains("shader"), "Shader incompatibility is reported instead of silent");
        backend.shaders = false;
        lighting.tick();
        check(backend.overridden() && backend.gamma() == 100F, "Leaving shaders applies the override");
        check(lighting.status().isEmpty(), "The shader warning clears");
    }

    private static void failedWrite(Path root) throws Exception {
        ModSettings settings = new ModSettings(root.toFile());
        FakeBackend backend = new FakeBackend();
        Lighting lighting = new Lighting(backend);
        settings.setEnabled(lighting.id(), true);
        lighting.attach(settings);
        lighting.setNumber(Lighting.METHOD, Lighting.GAMMA);
        lighting.tick();
        check(backend.gamma() == 100F, "The override is active before the write failure");
        Files.delete(root.resolve("comet/settings.json"));
        Files.createDirectories(root.resolve("comet/settings.json/occupied"));
        backend.editGamma(0.8F);
        lighting.tick();
        check(lighting.number(Lighting.METHOD) == Lighting.GAMMA, "A failed write keeps the gamma method selected");
        check(Boolean.TRUE.equals(settings.enabled(lighting.id())), "Failed disable preserves persisted enablement in memory");
        check(backend.gamma() == 0.8F, "The edited preference survives a failed write");
        lighting.tick();
        check(backend.gamma() == 0.8F, "The override is not reapplied after a failed write");
        check(lighting.status().contains("gamma"), "The automatic disable is reported after a failed write");
    }

    private static void compatibility(Path root) {
        Lighting lighting = new Lighting();
        ModSettings settings = new ModSettings(root.toFile());
        settings.setEnabled("lighting", true);
        lighting.attach(settings);
        check(!lighting.backend().available(), "The default constructor keeps a no-op backend");
        check(lighting.number(Lighting.MULTIPLIER) == 2, "The existing lightmap multiplier default is unchanged");
        check(Lighting.MULTIPLIER.display(2).equals("2x"), "The lightmap multiplier keeps its unit");
        check(Lighting.TOGGLE.defaultValue == 0, "The fullbright binding starts unbound");
        lighting.setNumber(Lighting.METHOD, Lighting.GAMMA);
        lighting.tick();
        check(lighting.status().contains("bridge"), "A missing version bridge is reported");
        int[] original = {0xFF204080, 0x80301005, 0xFF000000};
        int[] colors = original.clone();
        lighting.setNumber(Lighting.METHOD, Lighting.LIGHTMAP);
        lighting.setOption(Lighting.FULLBRIGHT, true);
        lighting.apply(colors);
        check(colors[0] == 0xFFFFFFFF && colors[1] == 0x80FFFFFF, "Lightmap fullbright still saturates RGB and keeps alpha");
        colors = original.clone();
        lighting.setOption(Lighting.FULLBRIGHT, false);
        lighting.setNumber(Lighting.MULTIPLIER, 3);
        lighting.apply(colors);
        check(colors[0] == 0xFF60C0FF, "Lightmap multipliers still brighten channels independently");
        lighting.setNumber(Lighting.METHOD, Lighting.GAMMA);
        colors = original.clone();
        lighting.apply(colors);
        check(Arrays.equals(colors, original), "Gamma mode leaves the lightmap untouched");
        lighting.setNumber(Lighting.METHOD, Lighting.LIGHTMAP);
        colors = original.clone();
        lighting.apply(colors);
        check(!Arrays.equals(colors, original), "Switching back to the lightmap method restores the transform");
    }

    private static ModRegistry registry(Path root, Lighting lighting) {
        ModSettings settings = new ModSettings(root.toFile());
        settings.setEnabled(lighting.id(), true);
        ModRegistry registry = new ModRegistry(settings);
        registry.register(lighting);
        return registry;
    }

    private static boolean close(double value, double expected) {
        return Math.abs(value - expected) < 1e-6;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeBackend implements LightingBackend {
        float preference = 0.3F;
        float saved = 0.3F;
        float persisted;
        float requested = Float.NaN;
        Float active;
        boolean edited;
        boolean shaders;
        int captures;

        @Override
        public boolean shadersActive() {
            return shaders;
        }

        @Override
        public float gamma() {
            return active == null ? preference : active;
        }

        @Override
        public void setGammaOverride(float gamma) {
            if (active == null) {
                saved = preference;
                captures++;
            }
            active = gamma;
            requested = gamma;
        }

        @Override
        public void removeGammaOverride() {
            if (active != null) {
                active = null;
                preference = saved;
            }
        }

        @Override
        public boolean consumeUserGammaEdit() {
            boolean value = edited;
            edited = false;
            return value;
        }

        boolean overridden() {
            return active != null;
        }

        void editGamma(float value) {
            if (active == null) {
                preference = value;
            } else {
                saved = value;
                edited = true;
            }
        }

        void save() {
            persisted = active == null ? preference : saved;
        }

        void load() {
            preference = persisted;
            if (active != null) {
                saved = persisted;
            }
        }
    }
}
