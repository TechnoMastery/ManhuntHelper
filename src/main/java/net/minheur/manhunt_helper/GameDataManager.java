package net.minheur.manhunt_helper;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameDataManager {
    private static final Gson gson = new Gson();
    private static Path file;

    public static Data data;

    public static void resetData() {
        // todo
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

        data = gson.fromJson(content, Data.class);
    }

    public static void save() {
        String s = getSavedAsJson();
        try {
            Files.writeString(file, s);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write current config file", e);
        }
    }

    public static @NonNull String getSavedAsJson() {
        return gson.toJson(data);
    }

    public static class Data {
        public boolean startCountdown = true;
        public int timerTicks = 2400;
        public Phase phase = Phase.WAITING;
        public int runnerLeft = 0;

        public boolean allowChooseTeam = true;
        public TeamChatMode teamChatMode = TeamChatMode.ALLOWED;
        public List<UUID> teamChatEnabledPlayers = new ArrayList<>();
    }

    public enum Phase {
        @SerializedName("waiting") WAITING,
        @SerializedName("config") CONFIG,
        @SerializedName("prep") PREPARE,
        @SerializedName("countdown") INITIAL_COUNTDOWN,
        @SerializedName("head_start") HEAD_START,
        @SerializedName("playing") PLAYING,
        @SerializedName("finished") FINISHED
    }
    public enum TeamChatMode {
        @SerializedName("forbidden") FORBIDDEN,
        @SerializedName("allowed") ALLOWED,
        @SerializedName("forced") FORCED
    }

}
