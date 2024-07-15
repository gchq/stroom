package stroom.job.client.event;

import stroom.job.client.event.OpenJobNodeEvent.OpenJobNodeHandler;
import stroom.job.shared.JobNode;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class OpenJobNodeEvent extends GwtEvent<OpenJobNodeHandler> {

    private static Type<OpenJobNodeHandler> TYPE;
    private final JobNode jobNode;

    private OpenJobNodeEvent(final JobNode jobNode) {
        this.jobNode = jobNode;
    }

    /**
     * Opens a job node on the jobs screen
     */
    public static void fire(final HasHandlers handlers, final JobNode jobNode) {
        handlers.fireEvent(new OpenJobNodeEvent(jobNode));
    }

    public static Type<OpenJobNodeHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenJobNodeHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenJobNodeHandler handler) {
        handler.onOpen(this);
    }

    public JobNode getJobNode() {
        return jobNode;
    }

    @Override
    public String toString() {
        return "OpenJobNodeEvent{" +
                "jobNode=" + jobNode +
                '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenJobNodeHandler extends EventHandler {

        void onOpen(OpenJobNodeEvent event);
    }
}
