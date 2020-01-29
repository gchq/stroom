package stroom.activity.shared;

public class ActivityValidationResult {
    private boolean valid;
    private String messages;

    public ActivityValidationResult() {
    }

    public ActivityValidationResult(final boolean valid, final String messages) {
        this.valid = valid;
        this.messages = messages;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(final boolean valid) {
        this.valid = valid;
    }

    public String getMessages() {
        return messages;
    }

    public void setMessages(final String messages) {
        this.messages = messages;
    }
}
