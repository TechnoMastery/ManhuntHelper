package net.minheur.manhunt_helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;
import net.minecraft.world.rule.GameRules;

import java.nio.file.Path;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ManhuntHelper implements ModInitializer {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Path worldPath;

    @Override
    public void onInitialize() {
        AllowedHostManager.load();

        ServerWorldEvents.LOAD.register(((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;

            worldPath = server.getSavePath(WorldSavePath.ROOT);
            GameDataManager.setup();

            if (GameDataManager.phase == GameDataManager.Phase.WAITING) {
                GameRules rules = world.getGameRules();
                rules.setValue(GameRules.DO_MOB_SPAWNING, false, server);
                rules.setValue(GameRules.SPAWN_MONSTERS, false, server);
            }
        }));

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
                            .then(literal("host")
                                    .requires(source -> {
                                        if (source.getEntity() instanceof ServerPlayerEntity player)
                                            return AllowedHostManager.isHost(player.getUuid());
                                        return false;
                                    })
                                    .then(literal("claim")
                                            .executes(context -> {
                                                if (GameDataManager.phase != GameDataManager.Phase.WAITING) {
                                                    context.getSource().sendError(Text.literal("Game has already been claimed."));
                                                    return 0;
                                                }
                                                ServerPlayerEntity host = context.getSource().getPlayerOrThrow();
                                                GameManager.setup(host);
                                                return 1;
                                            })
                                    )
                                    .then(literal("config")
                                            .then(literal("allowChooseTeam")
                                                    .then(argument("allows", BoolArgumentType.bool())
                                                            .executes(context -> {
                                                                if (GameDataManager.phase != GameDataManager.Phase.CONFIG) {
                                                                    context.getSource().sendError(Text.literal("Not in config stage."));
                                                                    return 0;
                                                                }
                                                                GameDataManager.allowChooseTeam = BoolArgumentType.getBool(context, "allows");
                                                                return 1;
                                                            })
                                                    )
                                            )
                                    )
                            )
            );
        }));

    }

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("manhunt-helper");
    }
}
