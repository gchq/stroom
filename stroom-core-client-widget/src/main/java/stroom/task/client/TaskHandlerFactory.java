package stroom.task.client;

public interface TaskHandlerFactory {

    TaskHandler createTaskHandler(String message);
}
