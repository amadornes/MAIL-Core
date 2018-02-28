package mail.core.loader;

import mail.api.event.EventBus;
import mail.api.loader.Loader;
import mail.api.loader.Mod;
import mail.api.loader.ModContainer;
import mail.api.loader.ModProvider;
import mail.api.serial.DataStructure;
import mail.core.event.EventBusImpl;
import mail.core.serial.JSONSerializationHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public enum LoaderImpl implements Loader {
    INSTANCE;

    private static final String METADATA_FILE_NAME = "mailmod.json";
    private static final String MODS_DIRECTORY_NAME = "mailmods";

    private final Set<ModContainer> containers = new HashSet<>();
    private final Map<ModProvider<?>, Set<? extends Mod.Prototype>> modPrototypes = new IdentityHashMap<>();
    private final Map<Mod.Prototype, Context> modContextMap = new IdentityHashMap<>();

    // TODO: Implement in a prettier way with some libs
    private final Set<String> modNames = new HashSet<>();
    private Set<ModImpl> modSet;

    @Override
    public boolean isLoaded(String id) {
        return modNames.contains(id);
    }

    @Override
    public Set<? extends Mod> getLoadedMods() {
        return modSet;
    }

    public void load(ClasspathManager manager) throws Exception {
        findMods(manager);
        findDependencies();
        injectDependencies();
        identifyMods();
        setupMods(manager);
        loadModules();
        loadMods();
    }

    private void findMods(ClasspathManager manager) throws IOException {
        File modsDir = new File("./" + MODS_DIRECTORY_NAME);
        modsDir.mkdirs();

        File[] jars = modsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        for (File jar : jars) {
            considerModContainer(jar.getCanonicalFile().getAbsoluteFile());
        }

        for (File file : manager.getClasspathSources()) {
            considerModContainer(file.getCanonicalFile().getAbsoluteFile());
        }
    }

    private void considerModContainer(File file) throws IOException {
        Path path = Paths.get(file.toURI());
        FileSystem fileSystem;
        Path fileSystemRoot;
        DataStructure metadata;

        if (file.getName().toLowerCase().endsWith(".jar")) {
            try (ZipFile jar = new ZipFile(file)) {
                ZipEntry metaFile = jar.getEntry(METADATA_FILE_NAME);
                if (metaFile == null) return; // Missing meta file - ignoring! TODO: Log warning

                fileSystem = FileSystems.newFileSystem(URI.create("jar:" + path.toUri().toString()), Collections.emptyMap());
                fileSystemRoot = fileSystem.getPath("/");
                metadata = JSONSerializationHandler.instance.read(jar.getInputStream(metaFile));
            } catch (IOException e) {
                return; // Jar is not a mod - ignoring!
            }
        } else if (file.isDirectory()) {
            File metaFile = new File(file, METADATA_FILE_NAME);
            if (!metaFile.exists()) return; // Missing meta file - ignoring!

            fileSystem = FileSystems.getDefault();
            fileSystemRoot = Paths.get(file.toURI());
            metadata = JSONSerializationHandler.instance.read(new FileInputStream(metaFile));
        } else {
            return; // Not something we can handle
        }

        containers.add(new JarModContainer(path, fileSystem, fileSystemRoot, metadata));
    }

    private void findDependencies() {
        // NO-OP for now
    }

    private void injectDependencies() {
        // NO-OP for now
    }

    private void identifyMods() {
        Set<DefaultModProvider.Prototype> defaultProto = DefaultModProvider.INSTANCE.identify(containers);
        modPrototypes.put(DefaultModProvider.INSTANCE, defaultProto);
    }

    private void setupMods(ClasspathManager manager) {
        Set<ModImpl> mods = new HashSet<>();
        for (Set<? extends Mod.Prototype> prototypes : modPrototypes.values()) {
            for (Mod.Prototype prototype : prototypes) {
                modContextMap.put(prototype, new Context(prototype, manager));
                modNames.add(prototype.getModID());
                mods.add(new ModImpl(prototype.getModID(), prototype.getName(), prototype.getVersion()));
            }
        }
        this.modSet = Collections.unmodifiableSet(mods);
    }

    private void loadModules() {

    }

    private void loadMods() throws Exception {
        for (Map.Entry<ModProvider<?>, Set<? extends Mod.Prototype>> entry : modPrototypes.entrySet()) {
            for (Mod.Prototype prototype : entry.getValue()) {
                ((ModProvider) entry.getKey()).preload(prototype, modContextMap.get(prototype));
            }
        }

        for (Map.Entry<ModProvider<?>, Set<? extends Mod.Prototype>> entry : modPrototypes.entrySet()) {
            for (Mod.Prototype prototype : entry.getValue()) {
                ((ModProvider) entry.getKey()).load(prototype, modContextMap.get(prototype));
            }
        }
    }

    private class Context implements ModContext {

        private final Mod.Prototype prototype;
        private final ClasspathManager manager;
        private final EventBus eventBus;

        public Context(Mod.Prototype prototype, ClasspathManager manager) {
            this.prototype = prototype;
            this.manager = manager;
            this.eventBus = new EventBusImpl(); // TODO: Replace with ASM-based event bus
        }

        @Override
        public ClassLoader getClassLoader() {
            return manager.getClassLoader();
        }

        @Override
        public void addSources(URL url) {
            manager.addSources(url);
        }

        @Override
        public EventBus getInternalEventBus() {
            return eventBus;
        }

    }

    public interface ClasspathManager {

        ClassLoader getClassLoader();

        File[] getClasspathSources();

        void addSources(URL url);

    }

}
