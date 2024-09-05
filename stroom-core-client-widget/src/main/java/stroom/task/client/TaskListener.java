package stroom.task.client;

public interface TaskListener {

    TaskHandler createTaskHandler(String message);
}
