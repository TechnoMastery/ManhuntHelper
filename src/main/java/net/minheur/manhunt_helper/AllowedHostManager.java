package net.minheur.manhunt_helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AllowedHostManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("manhunt-helper")
            .resolve("hosts.json");

    private static HostData data;

    public static void load() {
        try {
            Files.createDirectories(FILE.getParent());

            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    data = GSON.fromJson(reader, HostData.class);
                }
            }

            if (data == null) {
                data = new HostData();
                save();
            }
        } catch (IOException e) {
            throw new RuntimeException("Impossible to load hosts.json", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());

            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Impossible to save hosts.json", e);
        }
    }

    public static boolean addHost(UUID uuid) {
        if (data.hosts.contains(uuid.toString()))
            return false;
        data.hosts.add(uuid.toString());
        save();
        return true;
    }
    public static boolean removeHost(UUID uuid) {
        if (!data.hosts.remove(uuid.toString()))
            return false;
        save();
        return true;
    }
    public static boolean isHost(UUID uuid) {
        return data.hosts.contains(uuid.toString());
    }

    private static class HostData {
        List<String> hosts = new ArrayList<>();
    }
}
