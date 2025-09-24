package stroom.processor.task.client.event;

import stroom.processor.shared.ProcessorFilter;
import stroom.processor.task.client.event.OpenProcessorTaskEvent.Handler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.Objects;

public class OpenProcessorTaskEvent extends GwtEvent<Handler> {

    private static Type<Handler> TYPE;
    private final ProcessorFilter processorFilter;

    private OpenProcessorTaskEvent(final ProcessorFilter processorFilter) {
        this.processorFilter = Objects.requireNonNull(processorFilter);
    }

    public static void fire(final HasHandlers handlers, final ProcessorFilter processorFilter) {
        handlers.fireEvent(new OpenProcessorTaskEvent(
                Objects.requireNonNull(processorFilter, "Processor filter required")));
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
        handler.onOpen(this);
    }

    public ProcessorFilter getProcessorFilter() {
        return processorFilter;
    }

    @Override
    public String toString() {
        return "OpenProcessorTaskEvent{" +
               "processorFilter='" + processorFilter + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onOpen(OpenProcessorTaskEvent event);
    }
}
