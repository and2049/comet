package comet.mc1710.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("debugFPS")
    static int comet$debugFPS() {
        throw new AssertionError();
    }
}
