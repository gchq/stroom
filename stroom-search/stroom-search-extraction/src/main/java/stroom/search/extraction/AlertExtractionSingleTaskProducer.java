package stroom.search.extraction;

import stroom.alert.api.AlertDefinition;
import stroom.docref.DocRef;
import stroom.search.coprocessor.Receiver;
import stroom.search.extraction.ExtractionTaskProducer.ExtractionRunnable;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskExecutor;
import stroom.task.api.TaskProducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

class AlertExtractionSingleTaskProducer extends TaskProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertExtractionSingleTaskProducer.class);
    private static final String TASK_NAME = "Alert Extraction";

    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final Receiver receiver;
    private ExtractionTask task;
    private AtomicBoolean taskSupplied = new AtomicBoolean(false);

    AlertExtractionSingleTaskProducer(final long streamId,
                                      final long[] sortedEventIds,
                                      final DocRef extractionPipeline,
                                      final Receiver receiver,
                                      final List<AlertDefinition> alertDefinitions,
                                      final Map<String, String> params,
                                      final TaskExecutor taskExecutor,
                                      final Provider<ExtractionTaskHandler> handlerProvider,
                                      final TaskContextFactory taskContextFactory,
                                      final TaskContext parentContext) {
        super(taskExecutor, 1, taskContextFactory, parentContext, TASK_NAME);
        this.handlerProvider = handlerProvider;
        this.receiver = receiver;
        task = new ExtractionTask(streamId, sortedEventIds, extractionPipeline, receiver, alertDefinitions, params);
    }

    Receiver process (){
        // Attach to the supplied executor.
        attach();

        // Tell the supplied executor that we are ready to deliver tasks.
        signalAvailable();

        return receiver;
    }

    @Override
    protected synchronized Consumer<TaskContext> getNext() {
        if (taskSupplied.get() == false) {
            LOGGER.trace("Supplying task for stream " + task.getStreamId());
            incrementTasksTotal();
            taskSupplied.set(true);
            finishedAddingTasks();
            return new ExtractionRunnable(task, handlerProvider);
        }
        return null;
    }

    @Override
    protected void attach() {
        LOGGER.trace("Attaching producer for stream " + task.getStreamId());
        super.attach();
    }

    @Override
    protected void incrementTasksCompleted() {
        LOGGER.trace("Marking task is complete for stream " + task.getStreamId());
        super.incrementTasksCompleted();
    }

    @Override
    public int compareTo(final TaskProducer o) {
        if (! (o instanceof AlertExtractionSingleTaskProducer)){
            return super.compareTo(o);
        }
        AlertExtractionSingleTaskProducer other = (AlertExtractionSingleTaskProducer) o;
        int timeBased = super.compareTo(other);
        if (timeBased != 0){
            return timeBased;
        } else {
            return hashCode() - other.hashCode();
        }
    }
}
