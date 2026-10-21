package comet.mc189.mixin;

import comet.core.anim.ItemTransforms;
import comet.core.mod.OldAnimations;
import comet.mc189.Comet;
import comet.mc189.render.FirstPersonState;
import comet.mc189.render.GlTransform;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow
    private Minecraft mc;

    @Shadow
    private ItemStack itemToRender;

    @Unique
    private float comet$partialTicks;

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"))
    private void comet$remember(float partialTicks, CallbackInfo info) {
        comet$partialTicks = partialTicks;
    }

    @ModifyVariable(method = "transformFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float comet$oldSwing(float swingProgress) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || itemToRender == null) {
            return swingProgress;
        }
        OldAnimations visuals = Comet.client().oldAnimations();
        if (!Comet.client().mods().isEnabled(visuals)) {
            return swingProgress;
        }
        EnumAction action = itemToRender.getItemUseAction();
        if (player.getItemInUseCount() > 0 && !Comet.client().oldSwing(action == EnumAction.BLOCK)) {
            return swingProgress;
        }
        if (!visuals.firstPerson(comet$action(action))) {
            return swingProgress;
        }
        return visuals.swingProgress(player.getSwingProgress(comet$partialTicks), comet$partialTicks);
    }

    @ModifyArg(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;transformFirstPersonItem(FF)V", ordinal = 2), index = 0)
    private float comet$blockEquip(float equipProgress) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || itemToRender == null || player.getItemInUseCount() <= 0) {
            return equipProgress;
        }
        OldAnimations visuals = Comet.client().oldAnimations();
        if (!Comet.client().mods().isEnabled(visuals) || !visuals.itemTransforms()
                || !visuals.firstPerson(ItemTransforms.Action.BLOCK)) {
            return equipProgress;
        }
        float swing = visuals.swingProgress(player.getSwingProgress(comet$partialTicks), comet$partialTicks);
        return swing > 0.01F ? 0.0F : equipProgress;
    }

    @Inject(method = "doBowTransformations", at = @At("HEAD"), cancellable = true)
    private void comet$oldBow(float partialTicks, AbstractClientPlayer player, CallbackInfo info) {
        OldAnimations visuals = Comet.client().oldAnimations();
        if (itemToRender == null || !Comet.client().mods().isEnabled(visuals) || !visuals.itemTransforms()
                || !visuals.firstPerson(ItemTransforms.Action.BOW)) {
            return;
        }
        info.cancel();
        ItemTransforms.bow(GlTransform.INSTANCE, player.getItemInUseCount(), itemToRender.getMaxItemUseDuration(),
                partialTicks);
    }

    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V"))
    private void comet$oldItemTransforms(ItemRenderer renderer, EntityLivingBase entity, ItemStack stack,
            ItemCameraTransforms.TransformType type) {
        if (stack == null || type != ItemCameraTransforms.TransformType.FIRST_PERSON
                || !Comet.client().mods().isEnabled(Comet.client().oldAnimations()) || !comet$itemTransform(stack)) {
            renderer.renderItem(entity, stack, type);
            return;
        }
        boolean threeDimensional = mc.getRenderItem().shouldRenderItemIn3D(stack);
        GlStateManager.pushMatrix();
        boolean previous = FirstPersonState.push();
        try {
            ItemTransforms.flatItem(GlTransform.INSTANCE);
            if (threeDimensional) {
                ItemTransforms.carpet(GlTransform.INSTANCE);
                GlStateManager.translate(0.0F, -0.6F, 0.0F);
            }
            renderer.renderItem(entity, stack, type);
        } finally {
            FirstPersonState.restore(previous);
            GlStateManager.popMatrix();
        }
    }

    @Unique
    private boolean comet$itemTransform(ItemStack stack) {
        OldAnimations visuals = Comet.client().oldAnimations();
        if (!visuals.itemTransforms() || !visuals.firstPerson(comet$action(stack.getItemUseAction()))) {
            return false;
        }
        Item item = stack.getItem();
        if (item == Items.filled_map) {
            return false;
        }
        if (item instanceof ItemFishingRod || item == Items.carrot_on_a_stick) {
            if (!visuals.firstPersonFishingRod()) {
                return false;
            }
        }
        if (mc.getRenderItem().shouldRenderItemIn3D(stack)) {
            return visuals.firstPersonCarpet() && Block.getBlockFromItem(item) == Blocks.carpet;
        }
        return true;
    }

    @Unique
    private static ItemTransforms.Action comet$action(EnumAction action) {
        switch (action) {
            case EAT:
                return ItemTransforms.Action.EAT;
            case DRINK:
                return ItemTransforms.Action.DRINK;
            case BLOCK:
                return ItemTransforms.Action.BLOCK;
            case BOW:
                return ItemTransforms.Action.BOW;
            default:
                return ItemTransforms.Action.NONE;
        }
    }
}
