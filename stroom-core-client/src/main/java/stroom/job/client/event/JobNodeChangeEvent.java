package stroom.job.client.event;

import stroom.job.shared.JobNode;
import stroom.util.shared.NullSafe;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JobNodeChangeEvent extends GwtEvent<JobNodeChangeEvent.Handler> {

    private static Type<Handler> TYPE;
    private final List<JobNode> jobNodes;

    private JobNodeChangeEvent(final List<JobNode> jobNodes) {
        this.jobNodes = jobNodes;
    }

    private JobNodeChangeEvent(final JobNode jobNode) {
        this.jobNodes = Collections.singletonList(jobNode);
    }

    public static void fire(final HasHandlers handlers, final JobNode jobNode) {
        handlers.fireEvent(new JobNodeChangeEvent(jobNode));
    }

    public static void fire(final HasHandlers handlers, final List<JobNode> jobNodes) {
        handlers.fireEvent(new JobNodeChangeEvent(jobNodes));
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

    public List<JobNode> getJobNodes() {
        return jobNodes;
    }

    @Override
    public String toString() {
        return "JobNodeChangeEvent{" +
               "jobNodes=" + NullSafe.get(jobNodes, jobNodes2 -> jobNodes2
                .stream()
                .map(jobNode -> jobNode.getJobName() + " - " + jobNode.getNodeName() + " (" + jobNode.getId() + ")")
                .collect(Collectors.joining(", "))) +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onChange(JobNodeChangeEvent event);
    }
}
