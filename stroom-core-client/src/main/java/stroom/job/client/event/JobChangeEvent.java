package stroom.job.client.event;

import stroom.job.shared.Job;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class JobChangeEvent extends GwtEvent<JobChangeEvent.Handler> {

    private static Type<Handler> TYPE;
    private final Job job;

    private JobChangeEvent(final Job job) {
        this.job = job;
    }

    public static void fire(final HasHandlers handlers, final Job job) {
        handlers.fireEvent(new JobChangeEvent(job));
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
        handler.onChange(this);
    }

    public Job getJob() {
        return job;
    }

    @Override
    public String toString() {
        return "JobChangeEvent{" +
                "job=" + job +
                '}';
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onChange(JobChangeEvent event);
    }
}
