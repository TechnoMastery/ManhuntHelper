package net.minheur.manhunt_helper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameDataManager {
    private static final Gson gson = new Gson();
    private static Path file;

    public static boolean startCountdown = true;
    public static int timerTicks = 2400;
    public static Phase phase = Phase.WAITING;
    public static int runnerLeft = 0;

    public static boolean allowChooseTeam = true;
    public static TeamChatMode teamChatMode = TeamChatMode.ALLOWED;
    // only if ALLOWED is selected
    public static List<UUID> teamChatEnabledPlayers = new ArrayList<>();

    public static void resetData() {
        startCountdown = true;
        timerTicks = 2400;
        phase = Phase.WAITING;
        runnerLeft = 0;
        allowChooseTeam = true;
        teamChatMode = TeamChatMode.ALLOWED;
        teamChatEnabledPlayers = new ArrayList<>();
    }

    public static void setup() {
        file = ManhuntHelper.worldPath.resolve("manhunt_config.json");

        if (!Files.exists(file)) {
            String json = getSavedAsJson();
            try {
                Files.writeString(file, json);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create current config file", e);
            }
        }

        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read current config file", e);
        }

        loadFromJson(content);
    }

    public static void save() {
        String s = getSavedAsJson();
        try {
            Files.writeString(file, s);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write current config file", e);
        }
    }

    public static String getSavedAsJson() {
        JsonObject o = new JsonObject();
        o.addProperty("tmrTick", timerTicks);
        o.addProperty("phase", phase.coded);
        o.addProperty("runnerLeft", runnerLeft);
        o.addProperty("allowChooseTeam", allowChooseTeam);
        o.addProperty("teamChatMode", teamChatMode.coded);
        if (teamChatMode == TeamChatMode.ALLOWED)
            o.add("teamChatPlayers", gson.toJsonTree(teamChatEnabledPlayers));
        return o.toString();
    }
    public static void loadFromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        timerTicks = o.get("tmrTick").getAsInt();
        phase = Phase.getFromCoded(o.get("phase").getAsString());
        runnerLeft = o.get("runnerLeft").getAsInt();
        allowChooseTeam = o.get("allowChooseTeam").getAsBoolean();
        teamChatMode = TeamChatMode.getFromCoded(o.get("teamChatMode").getAsString());
        if (teamChatMode == TeamChatMode.ALLOWED)
            teamChatEnabledPlayers = gson.fromJson(o.get("teamChatPlayers").getAsJsonArray(),
                    new TypeToken<List<UUID>>() {}.getType());
        else teamChatEnabledPlayers = null;
    }

    public enum Phase {
        WAITING("waiting"),
        CONFIG("config"),
        PREPARE("prep"),
        INITIAL_COUNTDOWN("countdown"),
        HEAD_START("head_start"),
        PLAYING("playing"),
        FINISHED("finished");

        public final String coded;

        public static @Nullable Phase getFromCoded(String coded) {
            for (Phase p : Phase.values())
                if (p.coded.equals(coded)) return p;
            return null;
        }

        Phase(String coded) {
            this.coded = coded;
        }
    }
    public enum TeamChatMode {
        FORBIDDEN("forbidden"),
        ALLOWED("allowed"),
        FORCED("forced");

        public final String coded;

        public static @Nullable TeamChatMode getFromCoded(String coded) {
            for (TeamChatMode m : TeamChatMode.values())
                if (m.coded.equals(coded)) return m;
            return null;
        }

        TeamChatMode(String coded) {
            this.coded = coded;
        }
    }

}
