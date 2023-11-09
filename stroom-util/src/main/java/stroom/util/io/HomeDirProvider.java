package stroom.util.io;

import jakarta.inject.Provider;

import java.nio.file.Path;

public interface HomeDirProvider extends Provider<Path> {

    String PROP_STROOM_HOME = "stroom.home";
}
