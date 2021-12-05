package stroom.processor.client.presenter;

import stroom.docstore.shared.DocRefUtil;
import stroom.preferences.client.DateTimeFormatter;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorRow;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil.TableBuilder2;

import com.google.gwt.safehtml.shared.SafeHtml;

import javax.inject.Inject;

public class ProcessorInfoBuilder {

    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public ProcessorInfoBuilder(final DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
    }

    public SafeHtml get(final ProcessorListRow row) {
        final TooltipUtil.Builder builder = TooltipUtil.builder()
                .addTwoColTable(tableBuilder -> {
                    if (row instanceof ProcessorRow) {
                        final ProcessorRow processorRow = (ProcessorRow) row;
                        final Processor processor = processorRow.getProcessor();
                        tableBuilder.addHeaderRow("Stream Processor");
                        tableBuilder.addRow("Id", String.valueOf(processor.getId()));
                        tableBuilder.addRow("Created By", processor.getCreateUser());
                        addRowDateString(tableBuilder, "Created On", processor.getCreateTimeMs());
                        tableBuilder.addRow("Updated By", processor.getUpdateUser());
                        addRowDateString(tableBuilder, "Updated On", processor.getUpdateTimeMs());
                        tableBuilder.addRow("Pipeline",
                                DocRefUtil.createSimpleDocRefString(processor.getPipeline()));

                    } else if (row instanceof ProcessorFilterRow) {
                        final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                        final ProcessorFilter filter = processorFilterRow.getProcessorFilter();
                        tableBuilder.addHeaderRow("Stream Processor Filter");

                        if (filter.isReprocess()) {
                            tableBuilder.addBlankRow();
                            tableBuilder.addHeaderRow("This is a reprocessing filter");
                        }

                        if (filter.getMinMetaCreateTimeMs() != null ||
                                filter.getMaxMetaCreateTimeMs() != null) {
                            tableBuilder.addBlankRow();
                            tableBuilder.addHeaderRow("Constraints");
                            if (filter.getMinMetaCreateTimeMs() != null) {
                                tableBuilder.addRow("Min Stream Create Ms",
                                        ClientDateUtil.toISOString(filter.getMinMetaCreateTimeMs()));
                            }

                            if (filter.getMaxMetaCreateTimeMs() != null) {
                                tableBuilder.addRow("Max Stream Create Ms",
                                        ClientDateUtil.toISOString(filter.getMaxMetaCreateTimeMs()));
                            }
                            tableBuilder.addBlankRow();
                        }

                        tableBuilder.addRow("Id", filter.getId());
                        tableBuilder.addRow("Created By", filter.getCreateUser());
                        addRowDateString(tableBuilder, "Created On", filter.getCreateTimeMs());
                        tableBuilder.addRow("Updated By", filter.getUpdateUser());
                        addRowDateString(tableBuilder, "Updated On", filter.getUpdateTimeMs());
                        tableBuilder.addRow("Pipeline",
                                DocRefUtil.createSimpleDocRefString(filter.getPipeline()));

                        final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
                        if (tracker != null) {
                            addRowDateString(tableBuilder, "Min Stream Create Ms", tracker.getMinMetaCreateMs());
                            addRowDateString(tableBuilder, "Max Stream Create Ms", tracker.getMaxMetaCreateMs());
                            addRowDateString(tableBuilder, "Stream Create Ms", tracker.getMetaCreateMs());
                            tableBuilder.addRow("Stream Create %", tracker.getTrackerStreamCreatePercentage());
                            addRowDateString(tableBuilder, "Last Poll", tracker.getLastPollMs());
                            tableBuilder.addRow("Last Poll Age", tracker.getLastPollAge());
                            tableBuilder.addRow("Last Poll Task Count", tracker.getLastPollTaskCount());
                            tableBuilder.addRow("Min Stream Id", tracker.getMinMetaId());
                            tableBuilder.addRow("Min Event Id", tracker.getMinEventId());
                            tableBuilder.addRow("Streams", tracker.getMetaCount());
                            tableBuilder.addRow("Events", tracker.getEventCount());
                            tableBuilder.addRow("Status", tracker.getStatus());
                        }
                    }
                    return tableBuilder.build();
                });
        return builder.build();
    }

    private void addRowDateString(final TableBuilder2 builder, final String label, final Long ms) {
        if (ms != null) {
            builder.addRow(label, dateTimeFormatter.format(ms) + " (" + ms + ")");
        }
    }
}
