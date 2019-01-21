package stroom.task.api.job;

import stroom.task.shared.Task;

import java.util.function.Consumer;

public abstract class TaskConsumer implements Consumer<Task> {
    private final Consumer<Task> consumer;

    public TaskConsumer(final Consumer<Task> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void accept(final Task task) {
        consumer.accept(task);
    }
}