package stroom.pathways.impl;

import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.meta.api.MetaProperties;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.Inject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.function.Consumer;

public class MessageReceiverFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MessageReceiverFactory.class);

    private final Store streamStore;


    @Inject
    public MessageReceiverFactory(final Store streamStore) {
        this.streamStore = streamStore;
    }

    public void create(final String feedName, final Consumer<MessageReceiver> messageReceiverConsumer) {
        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(feedName)
                .typeName("Report")
//                .pipelineUuid(reportDoc.getUuid())
                .build();
        try {
            try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
                try (final OutputStreamProvider outputStreamProvider = streamTarget.next()) {
                    try (final Writer writer = new OutputStreamWriter(outputStreamProvider.get())) {
                        messageReceiverConsumer.accept((severity, message) -> {
                            try {
                                writer.write(severity.getDisplayValue());
                                writer.write(": ");
                                writer.write(message.get());
                                writer.write("\n");
                            } catch (final IOException | RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        });
                    }


//                        StreamUtil.streamToStream(inputStream, outputStreamProvider.get());
//
//                        try (final Writer writer = new OutputStreamWriter(outputStreamProvider.get(
//                                StreamTypeNames.META))) {
//                            write(writer, "ReportName", reportDoc.getName());
//                            write(writer,
//                                    "ReportDescription",
//                                    reportDoc.getDescription() != null
//                                            ? reportDoc.getDescription().replaceAll("\n", "")
//                                            : "");
//                            write(writer, "ExecutionTime",
//                                    DateUtil.createNormalDateTimeString(executionTime));
//                            write(writer, "EffectiveExecutionTime",
//                                    DateUtil.createNormalDateTimeString(effectiveExecutionTime));
//                        }
                }
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }
}
