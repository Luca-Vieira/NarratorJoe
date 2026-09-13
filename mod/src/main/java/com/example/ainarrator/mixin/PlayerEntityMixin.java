package com.example.ainarrator.mixin;

import com.example.ainarrator.collector.EventCollector;
import com.example.ainarrator.collector.GameEvent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.NamedScreenHandlerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "sendPickup", at = @At("HEAD"))
    private void onPickup(ItemEntity item, int count, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient()) return;

        String itemId = Registries.ITEM.getId(item.getStack().getItem()).toString();
        EventCollector.getInstance().add(new GameEvent.ItemPickupEvent(self.getName().getString(), itemId, count));
    }

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"))
    private void onDropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient()) return;

        if (!stack.isEmpty()) {
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            EventCollector.getInstance().add(new GameEvent.ItemDropEvent(self.getName().getString(), itemId, stack.getCount()));
        }
    }

    @Inject(method = "openHandledScreen", at = @At("HEAD"))
    private void onOpenHandledScreen(NamedScreenHandlerFactory factory, CallbackInfoReturnable<OptionalInt> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient()) return;

        if (factory != null) {
            String name = factory.getDisplayName().getString();
            EventCollector.getInstance().add(new GameEvent.ContainerOpenEvent(self.getName().getString(), name));
        }
    }
}
