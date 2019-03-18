package stroom.streamtask.server;

import java.nio.file.Path;

public interface ErrorReceiver {
    void onError(Path path, String message);
}