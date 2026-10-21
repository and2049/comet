package comet.mc189.mixin;

import comet.core.anim.ItemTransforms;
import comet.core.mod.OldAnimations;
import comet.mc189.Comet;
import comet.mc189.render.GlTransform;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerHeldItem.class)
public abstract class LayerHeldItemMixin {
    @Inject(method = "doRenderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V"))
    private void comet$thirdPersonHeldItems(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo info) {
        OldAnimations visuals = Comet.client().oldAnimations();
        if (!Comet.client().mods().isEnabled(visuals)) {
            return;
        }
        ItemStack stack = entity.getHeldItem();
        if (stack == null) {
            return;
        }
        if (entity instanceof EntityPlayer && ((EntityPlayer) entity).fishEntity != null) {
            stack = new ItemStack(Items.fishing_rod);
        }
        Item item = stack.getItem();
        if (comet$excluded(item)) {
            return;
        }
        boolean blocking = comet$blocking(entity);
        if (blocking && visuals.swings(true)) {
            ItemTransforms.thirdPersonBlock(GlTransform.INSTANCE);
        }
        if (visuals.thirdPersonHeldItems()) {
            ItemTransforms.thirdPersonHeld(GlTransform.INSTANCE, comet$kind(item), blocking, entity.isSneaking());
        }
    }

    @Unique
    private static boolean comet$excluded(Item item) {
        return item == Items.fishing_rod || item == Items.carrot_on_a_stick || item == Items.name_tag
                || item == Items.lead || item == Items.skull;
    }

    @Unique
    private static ItemTransforms.ThirdPerson comet$kind(Item item) {
        if (item == Items.stick || item == Items.blaze_rod || item instanceof ItemSword || item instanceof ItemSpade
                || item instanceof ItemAxe || item instanceof ItemHoe || item instanceof ItemPickaxe) {
            return ItemTransforms.ThirdPerson.TOOL;
        }
        if (item instanceof ItemBlock) {
            return ItemTransforms.ThirdPerson.FLAT;
        }
        return item == Items.bow ? ItemTransforms.ThirdPerson.BOW : ItemTransforms.ThirdPerson.OTHER;
    }

    @Unique
    private static boolean comet$blocking(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) entity;
        ItemStack active = player.getItemInUse();
        return player.isUsingItem() && active != null && active.getItemUseAction() == EnumAction.BLOCK;
    }
}
