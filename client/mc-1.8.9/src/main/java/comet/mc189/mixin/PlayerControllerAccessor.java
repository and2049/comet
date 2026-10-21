package comet.mc189.mixin;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerControllerMP.class)
public interface PlayerControllerAccessor {
    @Accessor("blockHitDelay")
    void comet$setBlockHitDelay(int delay);

    @Accessor("currentItemHittingBlock")
    void comet$setCurrentItemHittingBlock(ItemStack stack);
}
