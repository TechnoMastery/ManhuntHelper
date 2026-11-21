package net.minheur.manhunt_helper.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.TeamMsgCommand;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(TeamMsgCommand.class)
public interface TeamMsgAccessor {
    @Invoker("execute")
    static void invokeExecute(
            ServerCommandSource source,
            Entity entity,
            Team team,
            List<ServerPlayerEntity> recipients,
            SignedMessage message
    ) {
        throw new AssertionError();
    }
}
