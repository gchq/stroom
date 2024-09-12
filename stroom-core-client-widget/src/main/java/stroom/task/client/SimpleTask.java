package stroom.task.client;

public class SimpleTask implements Task {

    private final String message;

    public SimpleTask(final String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
    }
}
