package net.minheur.manhunt_helper;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.minheur.manhunt_helper.ManhuntHelper.getConfigDir;

public class DefaultDataManager {
    private static final Gson gson = ManhuntHelper.GSON;
    private static final Path FILE = getConfigDir().resolve("defaultConfig.json");
    private static GameDataManager.Data defaultData;

    public static void load() {
        try {
            Files.createDirectories(FILE.getParent());

            if (Files.exists(FILE)) try (Reader reader = Files.newBufferedReader(FILE)) {
                defaultData = gson.fromJson(reader, GameDataManager.Data.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Impossible to load defaultConfig.json", e);
        } finally {
            if (defaultData == null) {
                defaultData = new GameDataManager.Data();
                save();
            }
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());

            try (Writer writer = Files.newBufferedWriter(FILE)) {
                gson.toJson(defaultData, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Impossible to save defaultConfig.json", e);
        }
    }

    public static GameDataManager.Data getDefaultData() {
        return defaultData;
    }
}
