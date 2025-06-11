package stroom.planb.impl.data;

import stroom.util.string.StringIdUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SequentialFile {
    private final Path root;
    private final List<Path> subDirs;
    private final Path zip;

    public SequentialFile(final Path root,
                           final List<Path> subDirs,
                           final Path zip) {
        this.root = root;
        this.subDirs = subDirs;
        this.zip = zip;
    }

    public Path getRoot() {
        return root;
    }

    public List<Path> getSubDirs() {
        return subDirs;
    }

    public Path getZip() {
        return zip;
    }

    @Override
    public String toString() {
        return zip.toString();
    }
}
