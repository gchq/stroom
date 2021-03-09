package stroom.proxy.repo;

import java.nio.file.Path;

public interface ErrorReceiver {

    /**
     * Adds an error message to the repo for the specified path.
     *
     * @param path    The path of the input file we want to add an error message for.
     * @param message The message.
     */
    void onError(Path path, String message);
}
