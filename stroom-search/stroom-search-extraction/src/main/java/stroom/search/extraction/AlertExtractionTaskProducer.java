package stroom.search.extraction;

import stroom.alert.api.AlertDefinition;
import stroom.docref.DocRef;
import stroom.search.coprocessor.Receiver;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.List;
import java.util.Map;

class AlertExtractionTaskProducer extends ExtractionTaskProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertExtractionTaskProducer.class);

    AlertExtractionTaskProducer(final TaskExecutor taskExecutor,
                           final StreamMapCreator streamMapCreator,
                           final Receiver parentReceiver,
                           final Map<DocRef, Receiver> receivers,
                           final int maxStoredDataQueueSize,
                           final int maxThreadsPerTask,
                           final ExecutorProvider executorProvider,
                           final TaskContextFactory taskContextFactory,
                           final TaskContext parentContext,
                           final Provider<ExtractionTaskHandler> handlerProvider,
                           final SecurityContext securityContext) {
        super(taskExecutor, streamMapCreator, parentReceiver, receivers, maxStoredDataQueueSize, maxThreadsPerTask,
                executorProvider, taskContextFactory, parentContext, handlerProvider, securityContext);
    }

    public void createAlertExtractionTask(final long streamId, final long[] sortedEventIds, DocRef extractionPipeline,
                                          List<AlertDefinition> alertDefinitions, final Map<String, String> params, final Receiver receiver){
        final ExtractionTask task = new ExtractionTask(streamId, sortedEventIds, extractionPipeline, receiver, alertDefinitions, params);
        if (getTaskQueue().offer(new ExtractionRunnable(task, getHandlerProvider()))){
            LOGGER.debug("Created alert extraction task and submitted to queue");
        } else {
            LOGGER.error("Unable to submit alert extraction task to queue");
        }
        incrementTasksTotal();
    }

    @Override
    public Receiver process() {
        finishedAddingTasks();
        return super.process();
    }
}
