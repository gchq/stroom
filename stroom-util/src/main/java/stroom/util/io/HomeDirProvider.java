package stroom.util.io;

import java.nio.file.Path;
import jakarta.inject.Provider;

public interface HomeDirProvider extends Provider<Path> {

    String PROP_STROOM_HOME = "stroom.home";
}
