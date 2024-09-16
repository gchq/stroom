package stroom.task.client;

public interface TaskMonitor {

    void onStart(Task task);

    void onEnd(Task task);
}
