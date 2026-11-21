package net.minheur.manhunt_helper.mixin;

import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minheur.manhunt_helper.AllowedHostManager;
import net.minheur.manhunt_helper.GameDataManager;
import net.minheur.manhunt_helper.ManhuntHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(
            method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V",
            at = @At("HEAD"), cancellable = true
    )
    private void onPlayerChat(
            SignedMessage message,
            ServerPlayerEntity sender,
            MessageType.Parameters params,
            CallbackInfo ci
    ) {
        // phase check
        GameDataManager.Phase phase = GameDataManager.phase;
        if (phase == GameDataManager.Phase.WAITING
                || phase == GameDataManager.Phase.CONFIG
                || phase == GameDataManager.Phase.FINISHED
        ) return;

        // spec check - always send
        Team team = sender.getScoreboardTeam();
        if (team == null) return;

        GameDataManager.TeamChatMode teamChatMode = GameDataManager.teamChatMode;
        if ( // we automatically use team chat if not admin/host AND config set to FORCED
                teamChatMode == GameDataManager.TeamChatMode.FORCED &&
                        !(ManhuntHelper.isAdmin(sender) || AllowedHostManager.isHost(sender.getUuid()))
        ) {
            ci.cancel(); // we stop the message from being sent
            ManhuntHelper.sendAsTeam(sender, team, message); // send it as team
            return;
        }
        if ( // we send as plain message if not admin/host and it is forbidden
                teamChatMode == GameDataManager.TeamChatMode.FORBIDDEN &&
                        !(ManhuntHelper.isAdmin(sender) || AllowedHostManager.isHost(sender.getUuid()))
        ) return; // do to so we return without canceling ci
        // ELSE it is either allowed, or the player is admin/host. in that case we can just check if they enabled it
        if (GameDataManager.teamChatEnabledPlayers.contains(sender.getUuid())) { // enabled it
            ci.cancel();
            ManhuntHelper.sendAsTeam(sender, team, message);
        }
        // else let the method finish
    }

}
