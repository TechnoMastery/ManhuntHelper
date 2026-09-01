package net.minheur.manhunt_helper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GameDataManager {
    private static Path file;

    public static int timerTicks = 2400;
    public static Phase phase = Phase.WAITING;
    public static int runnerLeft = 0;

    public static boolean allowChooseTeam = true;

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
        return o.toString();
    }
    public static void loadFromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        timerTicks = o.get("tmrTick").getAsInt();
        phase = Phase.getFromCoded(o.get("phase").getAsString());
        runnerLeft = o.get("runnerLeft").getAsInt();
        allowChooseTeam = o.get("allowChooseTeam").getAsBoolean();
    }

    public enum Phase {
        WAITING("waiting"),
        CONFIG("config"),
        PREPARE("prep"),
        HEAD_START("head_start"),
        PLAYING("playing"),
        FINISHED("finished");

        public final String coded;

        public static Phase getFromCoded(String coded) {
            for (Phase p : Phase.values())
                if (p.coded.equals(coded)) return p;
            return null;
        }

        Phase(String coded) {
            this.coded = coded;
        }
    }

}
