package net.minheur.manhunt_helper.mixin;

import net.minecraft.network.message.SignedMessage;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.MessageCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minheur.manhunt_helper.AllowedHostManager;
import net.minheur.manhunt_helper.GameDataManager;
import net.minheur.manhunt_helper.ManhuntHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(MessageCommand.class)
public class MessageCommandMixin {

    @Inject(
            method = "execute",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void checkExecuteAllowed(ServerCommandSource source, Collection<ServerPlayerEntity> targets, SignedMessage message, CallbackInfo ci) {
        // check stage
        GameDataManager.Phase phase = GameDataManager.data.phase;
        if (phase == GameDataManager.Phase.WAITING
                || phase == GameDataManager.Phase.CONFIG
                || phase == GameDataManager.Phase.FINISHED
        ) return; // always allow direct message on these phases

        if (!source.isExecutedByPlayer()) return; // this is not a player - can't act
        ServerPlayerEntity sender = (ServerPlayerEntity) source.getEntity();
        if (sender == null) return;

        // if host or admin, we let them message
        if (ManhuntHelper.isAdmin(sender) || AllowedHostManager.isHost(sender.getUuid())) return;
        // else, we should apply rules.
        if (sender.getScoreboardTeam() == null) { // spectator
            GameDataManager.DirectMessageMode mode = GameDataManager.data.spectatorMessageMode;
            if (mode == GameDataManager.DirectMessageMode.FORBIDDEN) {
                source.sendError(Text.literal("Direct messages are forbidden for spectators."));
                ci.cancel();
            } else if (mode == GameDataManager.DirectMessageMode.EVERYONE) return; // anyone: let them play
            else { // else only to spectators ; should check
                boolean foundIncorrect = false;
                for (ServerPlayerEntity target : targets) if (target.getScoreboardTeam() != null) foundIncorrect = true;
                if (foundIncorrect) {
                    source.sendError(Text.literal("You can only message other spectators."));
                    ci.cancel();
                } // else let them message specs.
            }
        } else { // in a team
            GameDataManager.DirectMessageMode mode = GameDataManager.data.directMessageMode;
            if (mode == GameDataManager.DirectMessageMode.FORBIDDEN) {
                source.sendError(Text.literal("Direct messages are forbidden for players."));
                ci.cancel();
            } else if (mode == GameDataManager.DirectMessageMode.EVERYONE) return; // anyone: let them play
            else { // only with their team
                boolean foundIncorrect = false;
                Team senderTeam = sender.getScoreboardTeam();
                for (ServerPlayerEntity target : targets) if (target.getScoreboardTeam() != senderTeam) foundIncorrect = true;
                if (foundIncorrect) {
                    source.sendError(Text.literal("You can only message players of your team."));
                    ci.cancel();
                } // else let them message their team.
            }
        }
    }
}
