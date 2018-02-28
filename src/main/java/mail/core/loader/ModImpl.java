package mail.core.loader;

import mail.api.loader.Mod;
import mail.movetolib.version.Version;

public class ModImpl implements Mod {

    private final String modid, name;
    private final Version version;

    public ModImpl(String modid, String name, Version version) {
        this.modid = modid;
        this.name = name;
        this.version = version;
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
    public String toString() {
        return String.format("Mod(modid=\"%s\", name=\"%s\", version=\"%s\")", modid, name, version);
    }

}
