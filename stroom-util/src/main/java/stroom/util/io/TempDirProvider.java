package stroom.util.io;

import jakarta.inject.Provider;

import java.nio.file.Path;

public interface TempDirProvider extends Provider<Path> {
    String PROP_STROOM_TEMP = "stroom.temp";
}
