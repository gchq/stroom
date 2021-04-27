package stroom.proxy.repo;

import java.nio.file.Path;

public interface ErrorReceiver {

    /**
     * Adds an error message to the repo for the specified path.
     *
     * @param zipFile    The path of the input file we want to add an error message for.
     * @param message The message.
     */
    void error(Path zipFile, String message);

    /**
     * If you want to mark an input zip file as being bad so it is no longer used then call this method.
     *
     * @param zipFile    The path of the input file we want to add a fatal error message for.
     * @param message The message.
     */
    void fatal(Path zipFile, String message);
}
