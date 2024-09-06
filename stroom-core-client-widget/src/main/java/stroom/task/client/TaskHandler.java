package stroom.task.client;

public interface TaskHandler {

    void onStart(Task task);

    void onEnd(Task task);
}
