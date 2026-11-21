package net.minheur.manhunt_helper.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.MeCommand;
import net.minecraft.server.command.ServerCommandSource;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MeCommand.class)
public class MeCommandMixin {

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void blockMeCommand(CommandDispatcher<ServerCommandSource> dispatcher, @NonNull CallbackInfo ci) {
        ci.cancel();
    }

}
