package comet.launch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinTweaker;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigSource;

public class CometTweaker implements ITweaker {
    private static final String OPTIFINE_TRANSFORMER = "optifine.OptiFineClassTransformer";
    private final MixinTweaker mixin = new MixinTweaker();
    private final List<String> launchArguments = new ArrayList<String>();

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        mixin.acceptOptions(args, gameDir, assetsDir, profile);
        launchArguments.addAll(args);
        launchArguments.add("--gameDir");
        launchArguments.add(gameDir.getAbsolutePath());
        launchArguments.add("--assetsDir");
        launchArguments.add(assetsDir.getAbsolutePath());
        launchArguments.add("--version");
        launchArguments.add(profile);
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        boolean optifine = hasOptiFine(classLoader);
        if (optifine) {
            classLoader.addClassLoaderExclusion("optifine.");
            classLoader.registerTransformer(OPTIFINE_TRANSFORMER);
        }
        mixin.injectIntoClassLoader(classLoader);
        MixinEnvironment environment = MixinEnvironment.getDefaultEnvironment();
        if (environment.getObfuscationContext() == null) {
            environment.setObfuscationContext("notch");
        }
        environment.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("comet.mixins.json", (IMixinConfigSource) null);
        System.out.println(optifine ? "Comet client ready (OptiFine)" : "Comet client ready");
    }

    private static boolean hasOptiFine(LaunchClassLoader classLoader) {
        try {
            return classLoader.getClassBytes(OPTIFINE_TRANSFORMER) != null;
        } catch (IOException error) {
            return false;
        }
    }

    @Override
    public String getLaunchTarget() {
        return mixin.getLaunchTarget();
    }

    @Override
    public String[] getLaunchArguments() {
        return launchArguments.toArray(new String[launchArguments.size()]);
    }
}
