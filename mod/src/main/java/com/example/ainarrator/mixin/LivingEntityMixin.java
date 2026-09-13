package com.example.ainarrator.mixin;

import com.example.ainarrator.collector.EventCollector;
import com.example.ainarrator.collector.GameEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"))
    private void onAddStatusEffect(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof PlayerEntity player && !self.getWorld().isClient()) {
            String effectId = Registries.STATUS_EFFECT.getId(effect.getEffectType().value()).toString();
            EventCollector.getInstance().add(new GameEvent.EffectChangeEvent(effectId, effect.getAmplifier(), true));
        }
    }

    @Inject(method = "removeStatusEffect", at = @At("HEAD"))
    private void onRemoveStatusEffect(RegistryEntry<StatusEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof PlayerEntity player && !self.getWorld().isClient()) {
            String effectId = Registries.STATUS_EFFECT.getId(effect.value()).toString();
            EventCollector.getInstance().add(new GameEvent.EffectChangeEvent(effectId, 0, false));
        }
    }
}
