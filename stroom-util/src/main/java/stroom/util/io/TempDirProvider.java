package stroom.util.io;

import java.nio.file.Path;
import javax.inject.Provider;

public interface TempDirProvider extends Provider<Path> {
    String PROP_STROOM_TEMP = "stroom.temp";
}
