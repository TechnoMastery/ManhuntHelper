package net.minheur.manhunt_helper;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ManhuntHelper implements ModInitializer {

    @Override
    public void onInitialize() {
        AllowedHostManager.load();

        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("mh")
                            .then(literal("admin")
                                    .requires(source -> { // requires op
                                        if (source.getEntity() instanceof ServerPlayerEntity player)
                                            return source.getServer().getPlayerManager()
                                                    .isOperator(player.getPlayerConfigEntry());
                                        return true;
                                    })
                                    .then(literal("addHost")
                                            .then(argument("player", EntityArgumentType.player())
                                                    .executes(context -> {
                                                        // mh admin addHost <player>
                                                        ServerPlayerEntity player =
                                                                EntityArgumentType.getPlayer(context, "player");

                                                        if (AllowedHostManager.addHost(player.getUuid())) {
                                                            context.getSource().sendFeedback(
                                                                    () -> Text.literal(
                                                                            player.getName().getString() + " is now allowed to host."
                                                                    ), true
                                                            );
                                                            return 1;
                                                        }

                                                        context.getSource().sendError(Text.literal("This player is already allowed to host!"));
                                                        return 0;
                                                    })
                                            )
                                    )
                                    .then(literal("removeHost")
                                            .then(argument("player", EntityArgumentType.player())
                                                    .executes(context -> {
                                                        // mh admin removeHost <player>
                                                        ServerPlayerEntity player =
                                                                EntityArgumentType.getPlayer(context, "player");

                                                        if (AllowedHostManager.removeHost(player.getUuid())) {
                                                            context.getSource().sendFeedback(
                                                                    () -> Text.literal(
                                                                            player.getName().getString() + " is now unable to host."
                                                                    ), true
                                                            );
                                                            return 1;
                                                        }
                                                        context.getSource().sendError(Text.literal("This player is not allowed to host!"));
                                                        return 0;
                                                    })
                                            )
                                    )
                            )
                            .then(literal("claim")
                                    .requires(source -> {
                                        if (source.getEntity() instanceof ServerPlayerEntity player)
                                            return AllowedHostManager.isHost(player.getUuid());
                                        return false;
                                    })
                                    .executes(context -> {
                                        ServerPlayerEntity host = context.getSource().getPlayerOrThrow();
                                        GameManager.setup(host);
                                        return 1;
                                    })
                            )
            );
        }));

    }
}
