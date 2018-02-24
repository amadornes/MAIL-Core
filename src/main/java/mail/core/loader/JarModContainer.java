package mail.core.loader;

import mail.api.loader.ModContainer;
import mail.api.serial.DataStructure;

import java.nio.file.FileSystem;
import java.nio.file.Path;

public class JarModContainer implements ModContainer {

    private final Path path;
    private final FileSystem fileSystem;
    private final Path fileSystemRoot;
    private final DataStructure metadata;

    JarModContainer(Path path, FileSystem fileSystem, Path fileSystemRoot, DataStructure metadata) {
        this.path = path;
        this.fileSystem = fileSystem;
        this.fileSystemRoot = fileSystemRoot;
        this.metadata = metadata;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public FileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public Path getFileSystemRoot() {
        return fileSystemRoot;
    }

    @Override
    public DataStructure getMetadata() {
        return metadata;
    }

}
