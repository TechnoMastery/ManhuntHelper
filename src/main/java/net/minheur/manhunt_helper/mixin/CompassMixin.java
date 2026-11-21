package net.minheur.manhunt_helper.mixin;

import com.alphaduck.manhunt.ManHunt;
import com.alphaduck.manhunt.Runners;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.CompassItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.GlobalPos;
import net.minheur.manhunt_helper.ManhuntHelper;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(CompassItem.class)
public class CompassMixin {

    @Inject(
            method = "inventoryTick",
            at = @At("HEAD"),
            cancellable = true,
            order = 900
    )
    private void manhuntHelper$autoTrack(@NonNull ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || !data.copyNbt().contains("huntercompass")) return; // not a hunter's compass, we don't act.
        ci.cancel(); // make sure we cancel it

        // then, we update it:
        if (!world.isClient() && !ManHunt.isLocatorMode() && data.copyNbt().contains("huntercompass")) {
            PlayerEntity closestPlayer = ManhuntHelper.getClosestRunner(entity.getX(), entity.getY(), entity.getZ(), 1.3432324E7F, world, Runners.getRunners(world, false), null);
            if (closestPlayer != null && closestPlayer.getEntityWorld().getDimension() == entity.getEntityWorld().getDimension()) {
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.of("Tracking " + closestPlayer.getName().getString()));
                LodestoneTrackerComponent lodestoneTrackerComponent = new LodestoneTrackerComponent(Optional.of(GlobalPos.create(world.getRegistryKey(), closestPlayer.getBlockPos())), true);
                stack.set(DataComponentTypes.LODESTONE_TRACKER, lodestoneTrackerComponent);
            }
        }
    }

}
