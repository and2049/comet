package comet.core.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class ModSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File file;
    private Data data = new Data();

    public ModSettings(File gameDirectory) {
        this.file = new File(new File(gameDirectory, "comet"), "settings.json");
        load();
    }

    public Boolean enabled(String id) {
        return data.mods.get(id);
    }

    public void setEnabled(String id, boolean enabled) {
        data.mods.put(id, enabled);
        save();
    }

    public Layout layout(String id) {
        return data.hud.get(id);
    }

    public void setLayout(String id, Layout layout) {
        data.hud.put(id, layout);
        save();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try (Reader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) {
                if (loaded.mods == null) {
                    loaded.mods = new HashMap<String, Boolean>();
                }
                if (loaded.hud == null) {
                    loaded.hud = new HashMap<String, Layout>();
                }
                data = loaded;
            }
        } catch (IOException | RuntimeException error) {
            System.err.println("[Comet] Settings could not be read, using defaults: " + error.getMessage());
        }
    }

    private void save() {
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            System.err.println("[Comet] Settings folder could not be created: " + directory);
            return;
        }
        File temporary = new File(directory, file.getName() + ".tmp");
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(temporary.toPath()), StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (IOException error) {
            System.err.println("[Comet] Settings could not be written: " + error.getMessage());
            return;
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            System.err.println("[Comet] Settings could not be replaced: " + error.getMessage());
        }
    }

    public static final class Layout {
        public float x;
        public float y;
        public float scale = 1.0F;
        public boolean locked;
    }

    private static final class Data {
        Map<String, Boolean> mods = new HashMap<String, Boolean>();
        Map<String, Layout> hud = new HashMap<String, Layout>();
    }
}
