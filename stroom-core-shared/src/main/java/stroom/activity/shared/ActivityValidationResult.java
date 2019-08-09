package stroom.activity.shared;

import stroom.docref.SharedObject;

public class ActivityValidationResult implements SharedObject {
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

    public String getMessages() {
        return messages;
    }
}
