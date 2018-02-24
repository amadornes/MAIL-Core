package mail.core.loader;

import mail.api.loader.Loader;
import mail.api.loader.Mod;
import mail.api.loader.ModContainer;
import mail.api.loader.ModProvider;
import mail.api.serial.DataStructure;
import mail.movetolib.version.Version;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum DefaultModProvider implements ModProvider<DefaultModProvider.Prototype> {
    INSTANCE;

    @Override
    public Set<Prototype> identify(Set<ModContainer> containers) {
        Set<Prototype> prototypes = new HashSet<>();
        for (ModContainer container : containers) {
            prototypes.add(new Prototype(container));
        }
        return prototypes;
    }

    @Override
    public void preload(Prototype prototype, Loader.ModContext context) throws MalformedURLException {
        context.addSources(prototype.container.getPath().toUri().toURL());
    }

    @Override
    public void load(Prototype prototype, Loader.ModContext context) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Class<?> mainClass = Class.forName(prototype.mainClass, true, context.getClassLoader());
        Object instance = mainClass.newInstance();

        context.getInternalEventBus().register(instance);
    }

    public class Prototype implements Mod.Prototype {

        private final ModContainer container;

        private final String modid, name;
        private final Version version;

        private final Set<String> dependencies;

        private final String mainClass;

        private Prototype(ModContainer container) {
            this.container = container;

            DataStructure meta = this.container.getMetadata();

            this.modid = meta.get("modid", String.class);
            this.name = meta.get("name", String.class);
            String versionString = meta.get("version", String.class);
            this.version = null; // TODO: Version.parse(versionString);

            this.dependencies = Collections.emptySet(); // TODO: meta.streamAll("dependencies", String.class).collect(Collectors.toSet());

            this.mainClass = meta.get("main_class", String.class);
        }

        @Override
        public String getModID() {
            return modid;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Version getVersion() {
            return version;
        }

        @Override
        public Set<String> getDependencies() {
            return dependencies;
        }

        @Override
        public String toString() {
            return "DefaultModPrototype(modid=\"" + modid + "\")";
        }
    }

}
