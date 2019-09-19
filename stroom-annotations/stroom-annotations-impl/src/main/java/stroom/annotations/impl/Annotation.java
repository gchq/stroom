package stroom.annotations.impl;

public class Annotation {
    private final String user;
    private final String comment;
    private final String status;
    private final String assignee;

    public Annotation(final String user, final String comment, final String status, final String assignee) {
        this.user = user;
        this.comment = comment;
        this.status = status;
        this.assignee = assignee;
    }

    public String getUser() {
        return user;
    }

    public String getComment() {
        return comment;
    }

    public String getStatus() {
        return status;
    }

    public String getAssignee() {
        return assignee;
    }
}
