package net.minheur.manhunt_helper.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.TeamMsgCommand;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minheur.manhunt_helper.AllowedHostManager;
import net.minheur.manhunt_helper.GameDataManager;
import net.minheur.manhunt_helper.ManhuntHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(TeamMsgCommand.class)
public class TeamMsgMixin {

    @Inject(
            method = "execute",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void onExecute(
            ServerCommandSource source,
            Entity entity,
            Team team,
            List<ServerPlayerEntity> recipients,
            SignedMessage message,
            CallbackInfo ci
    ) {
        // check stage
        GameDataManager.Phase phase = GameDataManager.phase;
        if (phase == GameDataManager.Phase.WAITING
                || phase == GameDataManager.Phase.CONFIG
                || phase == GameDataManager.Phase.FINISHED
        ) {
            source.sendError(Text.literal("You cannot team chat when the game is not prepared"));
            ci.cancel();
            return;
        }
        if (!(entity instanceof ServerPlayerEntity player)) {
            ci.cancel();
            return;
        }

        if (!(ManhuntHelper.isAdmin(source, player) || AllowedHostManager.isHost(player.getUuid()))) // player not host nor admin
            if (GameDataManager.teamChatMode == GameDataManager.TeamChatMode.FORBIDDEN) { // and cannot team chat
                source.sendError(Text.literal("Team chat is FORBIDDEN for this game."));
                ci.cancel();
            }
    }

}
