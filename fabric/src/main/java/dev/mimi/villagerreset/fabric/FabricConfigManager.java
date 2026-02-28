package dev.cevapi.villagerreset.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FabricConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("villagerreset.json");

    private FabricConfigManager() {
    }

    public static FabricResetConfig load() {
        if (Files.notExists(PATH)) {
            FabricResetConfig defaults = new FabricResetConfig();
            save(defaults);
            return defaults;
        }
        try {
            String raw = Files.readString(PATH);
            FabricResetConfig loaded = GSON.fromJson(raw, FabricResetConfig.class);
            return loaded == null ? new FabricResetConfig() : loaded;
        } catch (Exception ignored) {
            return new FabricResetConfig();
        }
    }

    public static void save(FabricResetConfig config) {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(config));
        } catch (IOException ignored) {
        }
    }
}