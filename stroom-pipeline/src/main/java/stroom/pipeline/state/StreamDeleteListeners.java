package stroom.pipeline.state;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.pipeline.scope.PipelineScoped;

import java.util.ArrayList;
import java.util.List;

@PipelineScoped
public class StreamDeleteListeners implements StreamDeleteListener {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamDeleteListeners.class);
    private final List<StreamDeleteListener> listenerList = new ArrayList<>();

    public void add(final StreamDeleteListener streamDeleteListener) {
        listenerList.add(streamDeleteListener);
    }

    @Override
    public void delete(final long streamId) {
        for (final StreamDeleteListener listener : listenerList) {
            try {
                listener.delete(streamId);
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }
}
