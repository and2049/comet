package comet.mc189.mixin;

import comet.core.mod.OldAnimations;
import comet.core.ui.MainMenuScreen;
import comet.core.ui.PauseScreen;
import comet.core.ui.Screen;
import comet.mc189.Comet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderEye;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemExpBottle;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public WorldClient theWorld;

    @Shadow
    public int leftClickCounter;

    @Unique
    private boolean comet$attackClicked;

    @Unique
    private static final Set<Class<? extends Item>> COMET$USE_WHILE_DIGGING = new HashSet<Class<? extends Item>>(
            Arrays.asList(ItemEnderPearl.class, ItemEnderEye.class, ItemSnowball.class, ItemEgg.class,
                    ItemPotion.class, ItemExpBottle.class, ItemFishingRod.class));

    @Inject(method = "runTick", at = @At("HEAD"))
    private void comet$tick(CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean attack = mc.gameSettings != null && mc.gameSettings.keyBindAttack.isKeyDown();
        comet$attackClicked = false;
        Comet.client().tick();
        EntityPlayerSP player = mc.thePlayer;
        OldAnimations visuals = Comet.client().oldAnimations();
        if (player == null || !Comet.client().mods().isEnabled(visuals)) {
            return;
        }
        visuals.tickEyeHeight();
        boolean digging = player.getItemInUseCount() > 0 && attack && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
        visuals.tickSwing(comet$swingDuration(player), player.getSwingProgress(1.0F), player.getSwingProgress(0.0F),
                digging);
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void comet$useSwing(CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.currentScreen != null || !mc.inGameHasFocus
                || !mc.gameSettings.keyBindAttack.isKeyDown() || player.getItemInUseCount() <= 0) {
            return;
        }
        EnumAction action = player.getItemInUse().getItemUseAction();
        if (!Comet.client().oldSwing(action == EnumAction.BLOCK)) {
            return;
        }
        comet$swing(player);
    }

    @Inject(method = "clickMouse", at = @At("HEAD"))
    private void comet$alwaysSwing(CallbackInfo info) {
        comet$attackClicked = true;
        if (leftClickCounter <= 0) {
            return;
        }
        OldAnimations visuals = Comet.client().oldAnimations();
        if (!Comet.client().mods().isEnabled(visuals) || !visuals.alwaysSwing()) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            comet$swing(player);
        }
    }

    @Redirect(method = "rightClickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;getIsHittingBlock()Z"))
    private boolean comet$useWhileDigging(PlayerControllerMP controller) {
        Minecraft mc = Minecraft.getMinecraft();
        OldAnimations visuals = Comet.client().oldAnimations();
        if (comet$attackClicked || !controller.getIsHittingBlock() || !Comet.client().mods().isEnabled(visuals)
                || !visuals.useItemWhileDigging()) {
            return controller.getIsHittingBlock();
        }
        EntityPlayerSP player = mc.thePlayer;
        ItemStack stack = player == null ? null : player.getHeldItem();
        if (stack == null) {
            return controller.getIsHittingBlock();
        }
        Item item = stack.getItem();
        if (item instanceof ItemFood) {
            if (!player.canEat(((ItemFoodAccessor) item).comet$isAlwaysEdible())) {
                return controller.getIsHittingBlock();
            }
        } else if (stack.getItemUseAction() == EnumAction.NONE
                && !COMET$USE_WHILE_DIGGING.contains(item.getClass())) {
            return controller.getIsHittingBlock();
        }
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos pos = mc.objectMouseOver.getBlockPos();
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (block.getMaterial() != Material.air) {
                controller.resetBlockRemoving();
                ((PlayerControllerAccessor) controller).comet$setBlockHitDelay(5);
                ((PlayerControllerAccessor) controller).comet$setCurrentItemHittingBlock(null);
                return false;
            }
        }
        return controller.getIsHittingBlock();
    }

    @Inject(method = "displayGuiScreen", at = @At("HEAD"), cancellable = true)
    private void comet$swap(GuiScreen screen, CallbackInfo info) {
        Screen replacement = null;
        if (screen instanceof GuiMainMenu || (screen == null && theWorld == null)) {
            replacement = new MainMenuScreen(Comet.client());
        } else if (screen instanceof GuiIngameMenu) {
            replacement = new PauseScreen(Comet.client());
        }
        if (replacement != null) {
            info.cancel();
            Comet.client().host().openScreen(replacement);
        }
    }

    @Unique
    private static int comet$swingDuration(EntityPlayerSP player) {
        if (player.isPotionActive(Potion.digSpeed)) {
            return 6 - (1 + player.getActivePotionEffect(Potion.digSpeed).getAmplifier());
        }
        if (player.isPotionActive(Potion.digSlowdown)) {
            return 6 + (1 + player.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) * 2;
        }
        return 6;
    }

    @Unique
    private static void comet$swing(EntityPlayerSP player) {
        int duration = comet$swingDuration(player);
        if (!player.isSwingInProgress || player.swingProgressInt >= duration / 2 || player.swingProgressInt < 0) {
            player.swingProgressInt = -1;
            player.isSwingInProgress = true;
        }
    }
}
