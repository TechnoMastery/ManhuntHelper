package net.minheur.manhunt_helper;

import com.alphaduck.manhunt.ManHunt;
import com.alphaduck.manhunt.Runners;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.rule.GameRules;
import net.minheur.manhunt_helper.mixin.ManhuntModAccessor;

import java.util.ArrayList;

public class GameManager {

    public static ManhuntModAccessor manhuntMod;

    private static BlockPos markerPos;
    public static void tick(MinecraftServer server) {
        if (markerPos == null)
            markerPos = server.getOverworld()
                    .getEntitiesByType(EntityType.MARKER,
                            marker -> marker.getCommandTags().contains("spawn"))
                    .stream()
                    .map(marker -> marker.getBlockPos().up())
                    .findFirst()
                    .orElse(null);

        boolean giveEffects = GameDataManager.phase == GameDataManager.Phase.WAITING || GameDataManager.phase == GameDataManager.Phase.FINISHED;
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective deaths = scoreboard.getNullableObjective("deaths");
        AdvancementEntry advancementDragon = server.getAdvancementLoader().get(Identifier.of("minecraft", "end/kill_dragon"));

        boolean displayTimer = GameDataManager.phase == GameDataManager.Phase.HEAD_START;
        Text timerMessage = Text.empty()
                .append(Text.literal("Hunters are freed it ").formatted(Formatting.DARK_RED))
                .append(Text.literal(String.valueOf(GameDataManager.timerTicks / 20)).formatted(Formatting.DARK_RED, Formatting.BOLD))
                .append(Text.literal(" seconds !").formatted(Formatting.DARK_RED));

        // player tick
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {

            if (player.getCommandTags().contains("stuck") && markerPos != null)
                player.requestTeleport(markerPos.getX(), markerPos.getY(), markerPos.getZ());

            if (!(player.getCommandTags().contains("admin") || player.getCommandTags().contains("host") || player.getCommandTags().contains("player")))
                player.changeGameMode(GameMode.SPECTATOR);

            if (displayTimer) player.sendMessage(timerMessage, true);

            if (giveEffects) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 25));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 40, 255));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 255));
            }

            if (scoreboard.getScoreHolderTeam(player.getName().getString()).equals("runner") && (deaths != null && scoreboard.getScore(player, deaths).getScore() >= 1)) {
                server.getPlayerManager().broadcast(Text.empty()
                        .append(Text.literal("The runner ").formatted(Formatting.GOLD))
                        .append(Text.literal(player.getName().getString()).formatted(Formatting.GOLD, Formatting.BOLD))
                        .append(Text.literal(" is dead !").formatted(Formatting.GOLD)),
                        false
                );
                GameDataManager.runnerLeft --;
                scoreboard.getOrCreateScore(player, deaths).setScore(-1);
                player.changeGameMode(GameMode.SPECTATOR);
            }

            if (player.getAdvancementTracker().getProgress(advancementDragon).isDone())
                runnerWon(server);

        }

        if (GameDataManager.phase == GameDataManager.Phase.PLAYING && GameDataManager.runnerLeft <= 0)
            hunterWon(server);

        if (GameDataManager.phase == GameDataManager.Phase.HEAD_START)
            GameDataManager.timerTicks --;
        if (GameDataManager.timerTicks <= 0)
            freeHunters(server);
    }

    public static void cancel(ServerPlayerEntity host) {
        ServerWorld world = host.getEntityWorld();
        MinecraftServer server = world.getServer();
        Scoreboard scoreboard = server.getScoreboard();

        Runners.setRunners(new ArrayList<>());

        scoreboard.removeTeam(new Team(scoreboard, "runner"));
        scoreboard.removeTeam(new Team(scoreboard, "hunter"));

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.getInventory().clear();
            player.requestTeleport(host.getX(), host.getY(), host.getZ());
            player.getCommandTags().remove("stuck");
            player.getCommandTags().remove("player");
            player.changeGameMode(GameMode.ADVENTURE);
        }
        host.getCommandTags().remove("host");

        world.getWorldBorder().setSize(10);
        world.getGameRules().setValue(GameRules.SPAWN_MONSTERS, false, server);
        world.getGameRules().setValue(GameRules.DO_MOB_SPAWNING, false, server);

        server.getPlayerManager().broadcast(Text.empty()
                .append(Text.literal(host.getName().getString()).formatted(Formatting.BOLD, Formatting.ITALIC, Formatting.DARK_GRAY))
                .append(Text.literal(" has canceled his host!").formatted(Formatting.DARK_GRAY, Formatting.ITALIC)),
                false
        );

        MarkerEntity marker = world.getEntitiesByType(EntityType.MARKER,
                        m -> m.getCommandTags().contains("spawn"))
                .stream()
                .findFirst()
                .orElse(null);
        if (marker != null)
            marker.kill(world);
    }

    private static void hunterWon(MinecraftServer server) {
        globalFinish(server);

        Text title = Text.empty()
                .append(Text.literal("Huners WON").formatted(Formatting.GOLD));
        Text subtitle = Text.empty()
                .append(Text.literal("GGs").formatted(Formatting.GREEN, Formatting.GOLD));

        server.getPlayerManager().broadcast(title, false);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
                player.networkHandler.sendPacket(new TitleS2CPacket(title));
        }
    }
    private static void runnerWon(MinecraftServer server) {
        globalFinish(server);

        Text title = Text.empty()
                .append(Text.literal("Runners WON").formatted(Formatting.GOLD));
        Text subtitle = Text.empty()
                .append(Text.literal("GGs").formatted(Formatting.GREEN, Formatting.BOLD));

        server.getPlayerManager().broadcast(title, false);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
            player.networkHandler.sendPacket(new TitleS2CPacket(title));
        }
    }
    private static void globalFinish(MinecraftServer server) {
        GameDataManager.phase = GameDataManager.Phase.FINISHED;
        server.getOverworld().getGameRules().setValue(GameRules.PVP, false, server);

        server.getOverworld().getWorldBorder().setSize(10);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.requestTeleport(markerPos.getX(), markerPos.getY(), markerPos.getZ());
            player.changeGameMode(GameMode.ADVENTURE);
        }
    }

    private static void freeHunters(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        GameDataManager.phase = GameDataManager.Phase.PLAYING;

        Text runnerTitle = Text.empty()
                .append(Text.literal("Be careful !").formatted(Formatting.DARK_RED));
        Text runnerSubtitle = Text.empty()
                .append(Text.literal("Hunters have been ").formatted(Formatting.YELLOW))
                .append(Text.literal("freed").formatted(Formatting.YELLOW, Formatting.BOLD));
        Text hunterTitle = Text.empty()
                .append(Text.literal("Chase them down !").formatted(Formatting.RED));
        Text hunterSubtitle = Text.empty()
                .append(Text.literal("Don't wast your time").formatted(Formatting.YELLOW));

        server.getPlayerManager().broadcast(Text.empty()
                .append(Text.literal("Hunters freed").formatted(Formatting.BLUE, Formatting.BOLD, Formatting.ITALIC)),
                false
        );

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {

            if (scoreboard.getScoreHolderTeam(player.getName().getString()).equals("hunter")) {
                player.changeGameMode(GameMode.SURVIVAL);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100));
                player.giveItemStack(manhuntMod.mkCompass());

                player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(hunterSubtitle));
                player.networkHandler.sendPacket(new TitleS2CPacket(hunterTitle));
            }

            if (scoreboard.getScoreHolderTeam(player.getName().getString()).equals("runner")) {
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(runnerSubtitle));
                player.networkHandler.sendPacket(new TitleS2CPacket(runnerTitle));
            }

        }
    }

    public static void setup(ServerPlayerEntity host) {
        MinecraftServer server = host.getEntityWorld().getServer();
        ServerWorld world = host.getEntityWorld();

        BlockPos anchor = host.getBlockPos();

        double x = anchor.getX();
        double y = anchor.getY();
        double z = anchor.getZ();

        /*
         * HOST
         */

        host.addCommandTag("host");

        /*
         * DATA
         */

        GameDataManager.phase = GameDataManager.Phase.CONFIG;

        /*
         * WORLD BORDER
         */

        WorldBorder border = world.getWorldBorder();
        border.setCenter(x, z);
        border.setSize(10.0);

        /*
         * WORLD SPAWN
         */

        world.setSpawnPoint(
                new WorldProperties.SpawnPoint(
                        new GlobalPos(world.getRegistryKey(), anchor),
                        host.getYaw(),
                        host.getPitch()
                )
        );

        /*
         * GAME RULES
         */

        GameRules rules = world.getGameRules();

        rules.setValue(GameRules.RESPAWN_RADIUS, 0, server);
        rules.setValue(GameRules.LOCATOR_BAR, false, server);
        rules.setValue(GameRules.ADVANCE_TIME, false, server);
        rules.setValue(GameRules.ADVANCE_WEATHER, false, server);
        rules.setValue(GameRules.PVP, false, server);
        rules.setValue(GameRules.DO_IMMEDIATE_RESPAWN, true, server);

        /*
         * TIME & WEATHER
         */

        world.setTimeOfDay(1000);
        world.setWeather(
                6000, 0, false, false
        );

        /*
         * GAME MODES
         */

        assert server != null;
        server.setDefaultGameMode(GameMode.ADVENTURE);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
            if (player.getCommandTags().contains("host"))
                player.changeGameMode(GameMode.CREATIVE);
        else player.changeGameMode(GameMode.ADVENTURE);

        /*
         * TP
         */

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
            player.requestTeleport(x, y, z);

        /*
         * TEAMS
         */

        Scoreboard scoreboard = server.getScoreboard();

        Team runner = scoreboard.getTeam("runner");
        if (runner != null)
            scoreboard.removeTeam(runner);

        Team hunter = scoreboard.getTeam("hunter");
        if (hunter != null)
            scoreboard.removeTeam(hunter);

        runner = scoreboard.addTeam("runner");
        hunter = scoreboard.addTeam("hunter");

        runner.setColor(Formatting.GREEN);
        hunter.setColor(Formatting.BLUE);

        runner.setCollisionRule(Team.CollisionRule.NEVER);
        hunter.setCollisionRule(Team.CollisionRule.NEVER);

        runner.setPrefix(Text.literal("[RUNNER] "));
        hunter.setPrefix(Text.literal("[HUNTER] "));

        /*
         * DEATH detection
         */

        var deaths = scoreboard.getNullableObjective("deaths");
        if (deaths != null) scoreboard.removeObjective(deaths);

        deaths = scoreboard.addObjective(
                "deaths",
                ScoreboardCriterion.DEATH_COUNT,
                Text.literal("deaths"),
                ScoreboardCriterion.RenderType.INTEGER,
                false, null
        );

        /*
         * MARKER spawn
         */

        var marker = EntityType.MARKER.create(world, SpawnReason.COMMAND);
        if (marker != null) {
            marker.refreshPositionAndAngles(
                    x, y, z,
                    0, 0
            );
            marker.addCommandTag("spawn");
            world.spawnEntity(marker);
        }

        /*
         * FORCE LOAD
         */

        int chunkX = anchor.getX() >> 4;
        int chunkZ = anchor.getZ() >> 4;

        for (int cx = chunkX -1; cx <= chunkX; cx ++)
            for (int cz = chunkZ -1; cz <= chunkZ; cz ++)
                world.setChunkForced(cx, cz, true);

        /*
         * ANNOUNCE
         */

        manhuntMod = ((ManhuntModAccessor) FabricLoader.getInstance()
                .getModContainer("manhunt")
                .flatMap(mod -> FabricLoader.getInstance()
                        .getEntrypointContainers("main", ModInitializer.class)
                        .stream()
                        .filter(e -> e.getProvider() == mod)
                        .map(e -> (ManHunt) e.getEntrypoint())
                        .findFirst())
                .orElseThrow());
        manhuntMod.accessSetMod(host.getCommandSource(), false);

        server.getPlayerManager().broadcast(Text.empty()
                        .append(Text.literal("Game hosted by ").formatted(Formatting.GREEN))
                        .append(Text.literal(host.getName().getString()).formatted(Formatting.GOLD, Formatting.BOLD))
                        .append(Text.literal(" !").formatted(Formatting.GREEN)),
                false
        );

    }

    public static void prepare(ServerPlayerEntity host) {
        MinecraftServer server = host.getEntityWorld().getServer();
        Scoreboard scoreboard = server.getScoreboard();
        ServerWorld world = host.getEntityWorld();

        BlockPos spawnPos = world.getEntitiesByType(
                EntityType.MARKER, marker -> marker.getCommandTags().contains("spawn")
        ).getFirst().getBlockPos().up();

        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {

            if (scoreboard.getScoreHolderTeam(other.getName().getString()).equals("runner")) {
                other.getCommandTags().add("player");
                Runners.addRunner(other.getUuidAsString());
                GameDataManager.runnerLeft ++;
            }
            if (scoreboard.getScoreHolderTeam(other.getName().getString()).equals("hunter"))
                other.getCommandTags().add("player");
            if (other.getCommandTags().contains("player"))
                other.getCommandTags().add("stuck");

            other.changeGameMode(GameMode.SPECTATOR);
            other.setSpawnPoint(new ServerPlayerEntity.Respawn(
                    new WorldProperties.SpawnPoint(new GlobalPos(world.getRegistryKey(), spawnPos), 0f, 0f), true), false
            );
        }

        WorldBorder border = world.getWorldBorder();
        border.setSize(59999968.0);

        GameRules rules = world.getGameRules();
        rules.setValue(GameRules.SPAWN_MONSTERS, true, server);
        rules.setValue(GameRules.DO_MOB_SPAWNING, true, server);

        GameDataManager.phase = GameDataManager.Phase.PREPARE;

        server.getPlayerManager().broadcast(Text.literal("Preparing to start...").formatted(Formatting.DARK_PURPLE), false);
    }

    public static void run(ServerPlayerEntity host) {
        ServerWorld world = host.getEntityWorld();
        MinecraftServer server = world.getServer();
        Scoreboard scoreboard = server.getScoreboard();

        Text runnerTitle = Text.empty()
                .append(Text.literal("You're a ").formatted(Formatting.GREEN))
                .append(Text.literal("Runner").formatted(Formatting.GREEN, Formatting.BOLD));
        Text runnerSubtitle = Text.empty()
                .append(Text.literal("Finish Minecraft and ").formatted(Formatting.YELLOW))
                .append(Text.literal("survive !").formatted(Formatting.YELLOW, Formatting.BOLD));
        Text hunterTitle = Text.empty()
                .append(Text.literal("You're a ").formatted(Formatting.RED))
                .append(Text.literal("Hunter").formatted(Formatting.RED, Formatting.BOLD));
        Text hunterSubtitle = Text.empty()
                .append(Text.literal("The runners are going...").formatted(Formatting.YELLOW));

        server.getPlayerManager().broadcast(Text.empty()
                .append(Text.literal("Runners freed").formatted(Formatting.GREEN, Formatting.BOLD, Formatting.ITALIC)),
                false
        );

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {

            if (scoreboard.getScoreHolderTeam(player.getName().getString()).equals("runner")) {
                player.getCommandTags().remove("stuck");
                player.changeGameMode(GameMode.SURVIVAL);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100));

                player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(runnerSubtitle));
                player.networkHandler.sendPacket(new TitleS2CPacket(runnerTitle));
            }

            if (scoreboard.getScoreHolderTeam(player.getName().getString()).equals("hunter")) {
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(hunterSubtitle));
                player.networkHandler.sendPacket(new TitleS2CPacket(hunterTitle));
            }

        }

        GameDataManager.phase = GameDataManager.Phase.HEAD_START;

        GameRules rules = world.getGameRules();
        rules.setValue(GameRules.ADVANCE_TIME, true, server);
        rules.setValue(GameRules.ADVANCE_WEATHER, true, server);
        rules.setValue(GameRules.PVP, true, server);
        rules.setValue(GameRules.SPAWN_MONSTERS, true, server);
        rules.setValue(GameRules.DO_MOB_SPAWNING, true, server);
    }

}
