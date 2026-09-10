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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModSettings {
    public static final int MAX_PRESET_NAME = 24;
    private static final int MAX_PRESETS = 64;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File file;
    private Data data = new Data();

    public ModSettings(File gameDirectory) {
        this.file = new File(new File(gameDirectory, "comet"), "settings.json");
        load();
        if (data.presets.isEmpty()) {
            data.presets.put("Default", new Snapshot(data));
        }
        if (!data.presets.containsKey(data.activePreset)) {
            data.activePreset = data.presets.keySet().iterator().next();
        }
    }

    public Boolean enabled(String id) {
        return data.mods.get(id);
    }

    public void setEnabled(String id, boolean enabled) {
        data.mods.put(id, enabled);
        save();
    }

    public Boolean option(String id, String key) {
        Map<String, Boolean> values = data.options.get(id);
        return values == null ? null : values.get(key);
    }

    public void setOption(String id, String key, boolean value) {
        Map<String, Boolean> values = data.options.get(id);
        if (values == null) {
            values = new HashMap<String, Boolean>();
            data.options.put(id, values);
        }
        values.put(key, value);
        save();
    }

    public Layout layout(String id) {
        return data.hud.get(id);
    }

    public void setLayout(String id, Layout layout) {
        data.hud.put(id, layout);
        save();
    }

    public List<String> presetNames() {
        return new ArrayList<String>(data.presets.keySet());
    }

    public String activePreset() {
        return data.activePreset;
    }

    public boolean presetModified() {
        Snapshot saved = data.presets.get(data.activePreset);
        return !data.mods.equals(saved.mods) || !data.options.equals(saved.options) || !data.hud.equals(saved.hud);
    }

    public void createPreset(String name) {
        name = availableName(name, null);
        if (data.presets.size() >= MAX_PRESETS) {
            throw new IllegalArgumentException("Preset limit reached (" + MAX_PRESETS + ").");
        }
        Data next = copyData();
        next.presets.put(name, new Snapshot(data));
        next.activePreset = name;
        commit(next);
    }

    public void savePreset() {
        Data next = copyData();
        next.presets.put(next.activePreset, new Snapshot(data));
        commit(next);
    }

    public void loadPreset(String name) {
        Snapshot preset = requirePreset(name);
        Data next = copyData();
        next.apply(preset);
        next.activePreset = name;
        commit(next);
    }

    public void renamePreset(String name, String replacement) {
        requirePreset(name);
        replacement = availableName(replacement, name);
        Data next = copyData();
        next.presets.clear();
        for (Map.Entry<String, Snapshot> entry : data.presets.entrySet()) {
            next.presets.put(entry.getKey().equals(name) ? replacement : entry.getKey(), entry.getValue());
        }
        if (name.equals(next.activePreset)) {
            next.activePreset = replacement;
        }
        commit(next);
    }

    public void deletePreset(String name) {
        requirePreset(name);
        if (data.presets.size() == 1) {
            throw new IllegalArgumentException("Keep at least one preset.");
        }
        Data next = copyData();
        next.presets.remove(name);
        if (name.equals(next.activePreset)) {
            next.activePreset = next.presets.keySet().iterator().next();
            next.apply(next.presets.get(next.activePreset));
        }
        commit(next);
    }

    private Snapshot requirePreset(String name) {
        Snapshot preset = data.presets.get(name);
        if (preset == null) {
            throw new IllegalArgumentException("Preset not found.");
        }
        return preset;
    }

    private static boolean validName(String name) {
        if (name == null || name.trim().isEmpty() || name.length() > MAX_PRESET_NAME) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            if (Character.isISOControl(name.charAt(index)) || name.charAt(index) == '§') {
                return false;
            }
        }
        return true;
    }

    private String availableName(String name, String current) {
        name = name == null ? "" : name.trim();
        if (!validName(name)) {
            throw new IllegalArgumentException("Use a name of 1–" + MAX_PRESET_NAME + " characters.");
        }
        for (String existing : data.presets.keySet()) {
            if (!existing.equals(current) && existing.equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("A preset with that name already exists.");
            }
        }
        return name;
    }

    private Data copyData() {
        Data next = new Data();
        next.apply(data);
        next.presets.putAll(data.presets);
        next.activePreset = data.activePreset;
        return next;
    }

    private void commit(Data next) {
        if (!write(next)) {
            throw new IllegalStateException("Could not save presets. Check the game folder.");
        }
        data = next;
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try (Reader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) {
                loaded.apply(new Snapshot(loaded));
                Map<String, Snapshot> presets = new LinkedHashMap<String, Snapshot>();
                if (loaded.presets != null) {
                    for (Map.Entry<String, Snapshot> entry : loaded.presets.entrySet()) {
                        if (validName(entry.getKey()) && entry.getValue() != null && presets.size() < MAX_PRESETS) {
                            presets.put(entry.getKey(), new Snapshot(entry.getValue()));
                        }
                    }
                }
                loaded.presets = presets;
                data = loaded;
            }
        } catch (IOException | RuntimeException error) {
            System.err.println("[Comet] Settings could not be read, using defaults: " + error.getMessage());
        }
    }

    private void save() {
        write(data);
    }

    private boolean write(Data value) {
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            System.err.println("[Comet] Settings folder could not be created: " + directory);
            return false;
        }
        File temporary = new File(directory, file.getName() + ".tmp");
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(temporary.toPath()), StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        } catch (IOException error) {
            System.err.println("[Comet] Settings could not be written: " + error.getMessage());
            return false;
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException error) {
            System.err.println("[Comet] Settings could not be replaced: " + error.getMessage());
            return false;
        }
    }

    public static final class Layout {
        public float x;
        public float y;
        public float scale = 1.0F;
        public boolean locked;

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Layout)) {
                return false;
            }
            Layout layout = (Layout) other;
            return Float.compare(x, layout.x) == 0 && Float.compare(y, layout.y) == 0
                    && Float.compare(scale, layout.scale) == 0 && locked == layout.locked;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, scale, locked);
        }
    }

    private static class Snapshot {
        Map<String, Boolean> mods = new HashMap<String, Boolean>();
        Map<String, Layout> hud = new HashMap<String, Layout>();
        Map<String, Map<String, Boolean>> options = new HashMap<String, Map<String, Boolean>>();

        Snapshot() {
        }

        Snapshot(Snapshot source) {
            apply(source);
        }

        final void apply(Snapshot source) {
            mods = source.mods == null ? new HashMap<String, Boolean>() : new HashMap<String, Boolean>(source.mods);
            Map<String, Layout> copiedHud = new HashMap<String, Layout>();
            if (source.hud != null) {
                for (Map.Entry<String, Layout> entry : source.hud.entrySet()) {
                    Layout original = entry.getValue();
                    if (original != null) {
                        Layout layout = new Layout();
                        layout.x = original.x;
                        layout.y = original.y;
                        layout.scale = original.scale;
                        layout.locked = original.locked;
                        copiedHud.put(entry.getKey(), layout);
                    }
                }
            }
            hud = copiedHud;
            Map<String, Map<String, Boolean>> copiedOptions = new HashMap<String, Map<String, Boolean>>();
            if (source.options != null) {
                for (Map.Entry<String, Map<String, Boolean>> entry : source.options.entrySet()) {
                    if (entry.getValue() != null) {
                        copiedOptions.put(entry.getKey(), new HashMap<String, Boolean>(entry.getValue()));
                    }
                }
            }
            options = copiedOptions;
        }
    }

    private static final class Data extends Snapshot {
        Map<String, Snapshot> presets = new LinkedHashMap<String, Snapshot>();
        String activePreset;
    }
}
