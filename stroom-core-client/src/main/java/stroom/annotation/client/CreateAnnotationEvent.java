package stroom.annotation.client;

import stroom.annotation.shared.AnnotationTable;
import stroom.annotation.shared.EventId;
import stroom.util.shared.UserRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;

public class CreateAnnotationEvent extends GwtEvent<CreateAnnotationEvent.Handler> {

    private static Type<CreateAnnotationEvent.Handler> TYPE;

    private final String title;
    private final String subject;
    private final String status;
    private final UserRef assignTo;
    private final String comment;
    private final AnnotationTable table;
    private final List<EventId> linkedEvents;
    private final List<Long> linkedAnnotations;

    public CreateAnnotationEvent(final String title,
                                 final String subject,
                                 final String status,
                                 final UserRef assignTo,
                                 final String comment,
                                 final AnnotationTable table,
                                 final List<EventId> linkedEvents,
                                 final List<Long> linkedAnnotations) {
        this.title = title;
        this.subject = subject;
        this.status = status;
        this.assignTo = assignTo;
        this.comment = comment;
        this.table = table;
        this.linkedEvents = linkedEvents;
        this.linkedAnnotations = linkedAnnotations;
    }

    public static void fire(final HasHandlers source) {
        source.fireEvent(new CreateAnnotationEvent(
                "New Annotation",
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    }

    public static void fire(final HasHandlers source,
                            final String title,
                            final String subject,
                            final String status,
                            final UserRef assignTo,
                            final String comment,
                            final AnnotationTable table,
                            final List<EventId> linkedEvents,
                            final List<Long> linkedAnnotations) {
        source.fireEvent(new CreateAnnotationEvent(
                title,
                subject,
                status,
                assignTo,
                comment,
                table,
                linkedEvents,
                linkedAnnotations));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onCreate(this);
    }

    public String getTitle() {
        return title;
    }

    public String getSubject() {
        return subject;
    }

    public String getStatus() {
        return status;
    }

    public UserRef getAssignTo() {
        return assignTo;
    }

    public String getComment() {
        return comment;
    }

    public AnnotationTable getTable() {
        return table;
    }

    public List<EventId> getLinkedEvents() {
        return linkedEvents;
    }

    public List<Long> getLinkedAnnotations() {
        return linkedAnnotations;
    }

    public interface Handler extends EventHandler {

        void onCreate(CreateAnnotationEvent event);
    }
}
