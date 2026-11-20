package net.minheur.manhunt_helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;
import net.minecraft.world.rule.GameRules;
import net.minheur.manhunt_helper.mixin.ManhuntModAccessor;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.UUID;

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

        ServerTickEvents.END_SERVER_TICK.register(GameManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> GameDataManager.save());

        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("mh")
                            .then(literal("teamChat")
                                    .executes(context -> {
                                        // phase check
                                        GameDataManager.Phase phase = GameDataManager.phase;
                                        if (phase == GameDataManager.Phase.WAITING
                                                || phase == GameDataManager.Phase.CONFIG
                                                || phase == GameDataManager.Phase.FINISHED
                                        ) {
                                            context.getSource().sendError(Text.literal("Please wait for the game to prepare before running this command."));
                                            return 0;
                                        }
                                        // some data...
                                        GameDataManager.TeamChatMode chatMode = GameDataManager.teamChatMode;
                                        // check other mods
                                        if (chatMode == GameDataManager.TeamChatMode.FORBIDDEN)
                                            context.getSource().sendMessage(Text.literal("Team chat in DISABLED by the host."));
                                        else if (chatMode == GameDataManager.TeamChatMode.FORCED) {
                                            context.getSource().sendMessage(Text.literal("Team chat in FORCED by the host."));
                                            // if player we may have more to say
                                            if (context.getSource().getEntity() instanceof ServerPlayerEntity player) {
                                                if (isAdmin(context.getSource(), player)) { // ADMIN bypass
                                                    context.getSource().sendMessage(Text.literal("You are ADMIN and can still toggle your chat to global."));
                                                    if (GameDataManager.teamChatEnabledPlayers.contains(player.getUuid()))
                                                        context.getSource().sendMessage(Text.literal("You have Team Chat enabled for this game."));
                                                    else
                                                        context.getSource().sendMessage(Text.literal("You have Team Chat disabled for this game."));
                                                }
                                                if (AllowedHostManager.isHost(player.getUuid())) { // player is HOST
                                                    context.getSource().sendMessage(Text.literal("You are the HOST and can still toggle your chat to global."));
                                                    if (GameDataManager.teamChatEnabledPlayers.contains(player.getUuid()))
                                                        context.getSource().sendMessage(Text.literal("You have Team Chat enabled for this game."));
                                                    else
                                                        context.getSource().sendMessage(Text.literal("You have Team Chat disabled for this game."));
                                                }
                                            }
                                        }
                                        else {
                                            context.getSource().sendMessage(Text.literal("Team chat in ALLOWED by the host."));
                                            if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) return 1;
                                            UUID uuid = player.getUuid();
                                            if (GameDataManager.teamChatEnabledPlayers.contains(uuid))
                                                context.getSource().sendMessage(Text.literal("You have Team Chat enabled for this game."));
                                            else context.getSource().sendMessage(Text.literal("You have Team Chat disabled for this game."));
                                        }
                                        return 1;
                                    })
                                    .then(argument("enabled", BoolArgumentType.bool())
                                            .executes(context -> {
                                                if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player)) return 0; // only players
                                                if (
                                                        !(isAdmin(context.getSource(), player) || AllowedHostManager.isHost(player.getUuid())) // is not admin or host
                                                        && GameDataManager.teamChatMode != GameDataManager.TeamChatMode.ALLOWED // and team chat is not choosable
                                                ) { // we return an error
                                                    context.getSource().sendError(Text.literal("You cannot change your team chat mode : it is in " + GameDataManager.teamChatMode.coded));
                                                    return 0;
                                                }
                                                // else we allow changement
                                                boolean activated = BoolArgumentType.getBool(context, "enabled");
                                                if (activated) GameDataManager.teamChatEnabledPlayers.add(player.getUuid());
                                                else GameDataManager.teamChatEnabledPlayers.remove(player.getUuid());
                                                context.getSource().sendMessage(Text.literal("You team chat mode has been changed to " + activated));
                                                return 1;
                                            })
                                    )
                            )
                            .then(literal("unstuck").executes(context -> {
                                if (GameDataManager.phase != GameDataManager.Phase.WAITING) {
                                    context.getSource().sendError(Text.literal("Please be in WAITING phase before running this command."));
                                    return 0;
                                }
                                Entity toKill = context.getSource().getEntity();
                                if (toKill == null) return 0;
                                toKill.kill((ServerWorld) toKill.getEntityWorld());
                                context.getSource().sendFeedback(() -> Text.literal("Killed you to get back to correct spawn!"), true);
                                return 1;
                            }))
                            .then(literal("team")
                                    .then(literal("runner").executes(context -> {
                                        if (!isTeamChoiceAllowed(context)) return 0;
                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                        if (player == null) return 0;
                                        Scoreboard scoreboard = player.getEntityWorld().getScoreboard();
                                        scoreboard.addScoreHolderToTeam(player.getName().getString(), scoreboard.getTeam("runner"));
                                        context.getSource().sendFeedback(() -> Text.empty()
                                                .append(Text.literal("You successfully joined ").formatted(Formatting.DARK_GREEN))
                                                .append(Text.literal("runner").formatted(Formatting.GREEN, Formatting.BOLD))
                                                .append(Text.literal(" team !").formatted(Formatting.DARK_GREEN)), true);
                                        return 1;
                                    }))
                                    .then(literal("hunter").executes(context -> {
                                        if (!isTeamChoiceAllowed(context)) return 0;
                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                        if (player == null) return 0;
                                        Scoreboard scoreboard = player.getEntityWorld().getScoreboard();
                                        scoreboard.addScoreHolderToTeam(player.getName().getString(), scoreboard.getTeam("hunter"));
                                        context.getSource().sendFeedback(() -> Text.empty()
                                                .append(Text.literal("You successfully joined ").formatted(Formatting.DARK_GREEN))
                                                .append(Text.literal("hunter").formatted(Formatting.BLUE, Formatting.BOLD))
                                                .append(Text.literal(" team !").formatted(Formatting.DARK_GREEN)), true);
                                        return 1;
                                    }))
                                    .then(literal("spectator").executes(context -> {
                                        if (!isTeamChoiceAllowed(context)) return 0;
                                        ServerPlayerEntity player = context.getSource().getPlayer();
                                        if (player == null) return 0;
                                        Scoreboard scoreboard = player.getEntityWorld().getScoreboard();
                                        Team team = scoreboard.getScoreHolderTeam(player.getName().getString());
                                        if (team != null)
                                            scoreboard.removeScoreHolderFromTeam(player.getName().getString(), team);
                                        context.getSource().sendFeedback(() -> Text.empty()
                                                .append(Text.literal("You successfully became a ").formatted(Formatting.DARK_GREEN))
                                                .append(Text.literal("spectator").formatted(Formatting.WHITE, Formatting.BOLD))
                                                .append(Text.literal(" !").formatted(Formatting.DARK_GREEN)), true);
                                        return 1;
                                    }))
                            )
                            .then(literal("admin")
                                    .requires(source -> { // requires op
                                        if (source.getEntity() instanceof ServerPlayerEntity player)
                                            return isAdmin(source, player);
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
                                            .then(literal("teamChatMode")
                                                    .then(literal("allow")
                                                            .executes(context -> {
                                                                if (!isConfigStage(context)) return 0;
                                                                GameDataManager.teamChatMode = GameDataManager.TeamChatMode.ALLOWED;
                                                                context.getSource().sendFeedback(() -> Text.literal("Set team chat mode to allowed"), true);
                                                                return 1;
                                                            })
                                                    )
                                                    .then(literal("force")
                                                            .executes(context -> {
                                                                if (!isConfigStage(context)) return 0;
                                                                GameDataManager.teamChatMode = GameDataManager.TeamChatMode.FORCED;
                                                                context.getSource().sendFeedback(() -> Text.literal("Set team chat mode to forced"), true);
                                                                return 1;
                                                            })
                                                    )
                                                    .then(literal("forbid")
                                                            .executes(context -> {
                                                                if (!isConfigStage(context)) return 0;
                                                                GameDataManager.teamChatMode = GameDataManager.TeamChatMode.FORBIDDEN;
                                                                context.getSource().sendFeedback(() -> Text.literal("Set team chat mode to forbidden"), true);
                                                                return 1;
                                                            })
                                                    )
                                                    .executes(context -> {
                                                        if (!isConfigStage(context)) return 0;
                                                        String mode = GameDataManager.teamChatMode.coded;
                                                        context.getSource().sendFeedback(() -> Text.literal("Team chat mode is set to " + mode), true);
                                                        return 1;
                                                    })
                                            )
                                            .then(literal("allowChooseTeam")
                                                    .then(argument("allows", BoolArgumentType.bool())
                                                            .executes(context -> {
                                                                if (!isConfigStage(context)) return 0;
                                                                GameDataManager.allowChooseTeam = BoolArgumentType.getBool(context, "allows");
                                                                context.getSource().sendFeedback(() -> Text.literal("Set team choice to " + GameDataManager.allowChooseTeam), true);
                                                                return 1;
                                                            })
                                                    )
                                            )
                                            .then(literal("headstart")
                                                    .executes(context -> {
                                                        if (!isConfigStage(context)) return 0;
                                                        int headStartSeconds = GameDataManager.timerTicks / 20;
                                                        context.getSource().sendFeedback(() -> Text.literal("Current configured headstart: " + headStartSeconds), false);
                                                        return 1;
                                                    })
                                                    .then(argument("seconds", IntegerArgumentType.integer())
                                                            .executes(context -> {
                                                                if (!isConfigStage(context)) return 0;
                                                                GameDataManager.timerTicks = IntegerArgumentType.getInteger(context, "seconds") *20;
                                                                return 1;
                                                            })
                                                    )
                                            )
                                            .then(literal("startTimer")
                                                    .then(argument("enabled", BoolArgumentType.bool())
                                                            .executes(context -> {
                                                                if (!isConfigStage(context)) return 0;
                                                                GameDataManager.startCountdown = BoolArgumentType.getBool(context, "enabled");
                                                                context.getSource().sendFeedback(() -> Text.literal("Set start time to " + GameDataManager.startCountdown), true);
                                                                return 1;
                                                            })
                                                    )
                                            )
                                    )
                                    .then(literal("team")
                                            .then(argument("player", EntityArgumentType.player())
                                                    .then(literal("runner").executes(context -> {
                                                        if (!isConfigStage(context)) return 0;
                                                        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                                        Scoreboard scoreboard = player.getEntityWorld().getScoreboard();
                                                        scoreboard.addScoreHolderToTeam(player.getName().getString(), scoreboard.getTeam("runner"));
                                                        context.getSource().sendFeedback(() -> Text.empty()
                                                                .append(Text.literal("Player ").formatted(Formatting.DARK_GREEN))
                                                                .append(Text.literal(player.getName().getString()).formatted(Formatting.GOLD, Formatting.UNDERLINE))
                                                                .append(Text.literal(" has been added to the ").formatted(Formatting.DARK_GREEN))
                                                                .append(Text.literal("runner").formatted(Formatting.GREEN, Formatting.BOLD))
                                                                .append(Text.literal(" team !").formatted(Formatting.DARK_GREEN)), true);
                                                        player.getCommandSource().sendFeedback(() -> Text.empty()
                                                                .append(Text.literal("The host added you to the ").formatted(Formatting.DARK_GREEN))
                                                                .append(Text.literal("runner").formatted(Formatting.GREEN, Formatting.BOLD))
                                                                .append(Text.literal(" team !").formatted(Formatting.DARK_GREEN)), false);
                                                        return 1;
                                                    }))
                                                    .then(literal("hunter").executes(context -> {
                                                        if (!isConfigStage(context)) return 0;
                                                        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                                        Scoreboard scoreboard = player.getEntityWorld().getScoreboard();
                                                        scoreboard.addScoreHolderToTeam(player.getName().getString(), scoreboard.getTeam("hunter"));
                                                        context.getSource().sendFeedback(() -> Text.empty()
                                                                .append(Text.literal("Player ").formatted(Formatting.DARK_GREEN))
                                                                .append(Text.literal(player.getName().getString()).formatted(Formatting.GOLD, Formatting.UNDERLINE))
                                                                .append(Text.literal(" has been added to the ").formatted(Formatting.DARK_GREEN))
                                                                .append(Text.literal("hunter").formatted(Formatting.GREEN, Formatting.BOLD))
                                                                .append(Text.literal(" team !").formatted(Formatting.DARK_GREEN)), true);
                                                        player.getCommandSource().sendFeedback(() -> Text.empty()
                                                                .append(Text.literal("The host added you to the ").formatted(Formatting.DARK_GREEN))
                                                                .append(Text.literal("hunter").formatted(Formatting.GREEN, Formatting.BOLD))
                                                                .append(Text.literal(" team !").formatted(Formatting.DARK_GREEN)), false);
                                                        return 1;
                                                    }))
                                                    .then(literal("spectator").executes(context -> {
                                                        if (!isConfigStage(context)) return 0;
                                                        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                                                        Scoreboard scoreboard = player.getEntityWorld().getScoreboard();
                                                        Team team = scoreboard.getScoreHolderTeam(player.getName().getString());
                                                        if (team != null)
                                                            scoreboard.removeScoreHolderFromTeam(player.getName().getString(), team);
                                                        context.getSource().sendFeedback(() -> Text.empty()
                                                                .append(Text.literal("Player ").formatted(Formatting.DARK_GREEN))
                                                                .append(Text.literal(player.getName().getString()).formatted(Formatting.GOLD, Formatting.UNDERLINE))
                                                                .append(Text.literal(" has been set to a ").formatted(Formatting.DARK_GREEN))
                                                                .append(Text.literal("spectator").formatted(Formatting.GREEN, Formatting.BOLD))
                                                                .append(Text.literal(" !").formatted(Formatting.DARK_GREEN)), true);
                                                        player.getCommandSource().sendFeedback(() -> Text.empty()
                                                                .append(Text.literal("The host said you'd be a ").formatted(Formatting.DARK_GREEN))
                                                                .append(Text.literal("spectator").formatted(Formatting.GREEN, Formatting.BOLD))
                                                                .append(Text.literal(" !").formatted(Formatting.DARK_GREEN)), false);
                                                        return 1;
                                                    }))
                                            )
                                    )
                                    .then(literal("prepare").executes(context -> {
                                        if (!isConfigStage(context)) return 0;
                                        ServerPlayerEntity host = context.getSource().getPlayerOrThrow();
                                        GameManager.prepare(host);
                                        return 1;
                                    }))
                                    .then(literal("start").executes(context -> {
                                        assert context.getSource().getEntity() != null;
                                        if (!context.getSource().getEntity().getCommandTags().contains("host")) {
                                            context.getSource().sendError(Text.literal("You are not this game's host!"));
                                            return 0;
                                        }
                                        if (GameDataManager.phase != GameDataManager.Phase.PREPARE) {
                                            context.getSource().sendError(Text.literal("Game is not prepared."));
                                            return 0;
                                        }
                                        ServerPlayerEntity host = context.getSource().getPlayerOrThrow();
                                        GameManager.run(host);
                                        return 1;
                                    }))
                                    .then(literal("close").executes(context -> {
                                        assert context.getSource().getEntity() != null;
                                        if (!context.getSource().getEntity().getCommandTags().contains("host")) {
                                            context.getSource().sendError(Text.literal("You are not this game's host!"));
                                            return 0;
                                        }
                                        if (GameDataManager.phase != GameDataManager.Phase.FINISHED) {
                                            context.getSource().sendError(Text.literal("You need to finish the game before deleting and resetting the server."));
                                            return 0;
                                        }
                                        ManhuntModAccessor.setResetReqAt(System.currentTimeMillis());
                                        ManhuntModAccessor.reset(context.getSource());
                                        return 0;
                                    }))
                                    .then(literal("cancel").executes(context -> {
                                        assert context.getSource().getEntity() != null;
                                        if (!context.getSource().getEntity().getCommandTags().contains("host")) {
                                            context.getSource().sendError(Text.literal("You are not this game's hsot!"));
                                            return 0;
                                        }
                                        if (!(GameDataManager.phase == GameDataManager.Phase.CONFIG || GameDataManager.phase == GameDataManager.Phase.PREPARE)) {
                                            context.getSource().sendError(Text.literal("You can't cancel now."));
                                            return 0;
                                        }
                                        ServerPlayerEntity host = context.getSource().getPlayerOrThrow();
                                        GameManager.cancel(host);
                                        return 1;
                                    }))
                            )
            );
        }));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;

            ServerPlayerEntity host = player.getEntityWorld().getServer()
                    .getPlayerManager()
                    .getPlayerList()
                    .stream()
                    .filter(p -> p.getCommandTags().contains("host"))
                    .findFirst()
                    .orElse(null);

            Text message;
            // waiting and can host
            if (GameDataManager.phase == GameDataManager.Phase.WAITING && AllowedHostManager.isHost(player.getUuid()))
                message = Text.empty().append(Text.literal("Hello host ").formatted(Formatting.GREEN)).append(Text.literal(player.getName().getString()).formatted(Formatting.GREEN, Formatting.BOLD))
                                        .append(Text.literal(" ! (").formatted(Formatting.GREEN)).append(Text.literal("/mh host claim").formatted(Formatting.GOLD))
                                        .append(Text.literal(")").formatted(Formatting.GREEN));
            // waiting and can't host / config / preparing
            else if (GameDataManager.phase == GameDataManager.Phase.WAITING || GameDataManager.phase == GameDataManager.Phase.CONFIG || GameDataManager.phase == GameDataManager.Phase.PREPARE)
                message = Text.empty().append(Text.literal("Hello ").formatted(Formatting.GREEN)).append(Text.literal(player.getName().getString()).formatted(Formatting.GREEN, Formatting.BOLD))
                        .append(Text.literal(" ! This game is waiting. Your host is ").formatted(Formatting.GREEN))
                        .append(Text.literal(host == null ? "unknown" : host.getName().getString()).formatted(Formatting.GREEN, Formatting.BOLD));
            // game finished
            else if (GameDataManager.phase == GameDataManager.Phase.FINISHED)
                message = Text.literal("Game is finished!").formatted(Formatting.GREEN, Formatting.BOLD);
            // else game is running
            else message = Text.empty().append(Text.literal("Hello ").formatted(Formatting.GREEN)).append(Text.literal(player.getName().getString()).formatted(Formatting.GREEN, Formatting.BOLD))
                        .append(Text.literal(" ! Game is running. The host is ").formatted(Formatting.GREEN))
                        .append(Text.literal(host == null ? "unknown" : host.getName().getString()).formatted(Formatting.GREEN, Formatting.BOLD));

            player.sendMessage(message, false);
        });

    }

    private static boolean isAdmin(@NonNull ServerCommandSource source, @NonNull ServerPlayerEntity player) {
        return source.getServer().getPlayerManager()
                .isOperator(player.getPlayerConfigEntry());
    }

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("manhunt-helper");
    }

    private static boolean isTeamChoiceAllowed(CommandContext<ServerCommandSource> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayerEntity player))
            return false;
        if (GameDataManager.phase != GameDataManager.Phase.CONFIG) {
            context.getSource().sendError(Text.literal("Need to be in config state to choose your team."));
            return false;
        }
        if (!GameDataManager.allowChooseTeam && !player.getCommandTags().contains("host")) {
            context.getSource().sendError(Text.literal("This game's host disabled team choice."));
            return false;
        }
        return true;
    }

    public static boolean isConfigStage(CommandContext<ServerCommandSource> context) {
        assert context.getSource().getEntity() != null;
        if (!context.getSource().getEntity().getCommandTags().contains("host")) {
            context.getSource().sendError(Text.literal("You are not this game's host!"));
            return false;
        }
        if (GameDataManager.phase != GameDataManager.Phase.CONFIG) {
            context.getSource().sendError(Text.literal("Not in config stage."));
            return false;
        }
        return true;
    }
}
