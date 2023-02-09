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
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.safehtml.shared.SafeHtml;

import javax.inject.Inject;

public class ProcessorInfoBuilder {

    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public ProcessorInfoBuilder(final DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
    }

    public SafeHtml get(final ProcessorListRow row) {
        final TableBuilder tb = new TableBuilder();
        if (row instanceof ProcessorRow) {
            final ProcessorRow processorRow = (ProcessorRow) row;
            final Processor processor = processorRow.getProcessor();
            tb.row(TableCell.header("Stream Processor", 2))
                    .row("Id", String.valueOf(processor.getId()))
                    .row("Created By", processor.getCreateUser());
            addRowDateString(tb, "Created On", processor.getCreateTimeMs());
            tb.row("Updated By", processor.getUpdateUser());
            addRowDateString(tb, "Updated On", processor.getUpdateTimeMs());
            tb.row("Pipeline",
                    DocRefUtil.createSimpleDocRefString(processor.getPipeline()));

        } else if (row instanceof ProcessorFilterRow) {
            final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
            final ProcessorFilter filter = processorFilterRow.getProcessorFilter();
            tb.row(TableCell.header("Stream Processor Filter", 2));

            if (filter.isReprocess()) {
                tb
                        .row()
                        .row(TableCell.header("This is a reprocessing filter", 2));
            }

            if (filter.getMinMetaCreateTimeMs() != null ||
                    filter.getMaxMetaCreateTimeMs() != null) {
                tb
                        .row()
                        .row(TableCell.header("Constraints", 2));
                if (filter.getMinMetaCreateTimeMs() != null) {
                    tb.row("Min Stream Create Ms",
                            ClientDateUtil.toISOString(filter.getMinMetaCreateTimeMs()));
                }

                if (filter.getMaxMetaCreateTimeMs() != null) {
                    tb.row("Max Stream Create Ms",
                            ClientDateUtil.toISOString(filter.getMaxMetaCreateTimeMs()));
                }
                tb.row();
            }

            tb.row(SafeHtmlUtil.from("Id"), SafeHtmlUtil.from(filter.getId()));
            tb.row("Created By", filter.getCreateUser());
            addRowDateString(tb, "Created On", filter.getCreateTimeMs());
            tb.row("Updated By", filter.getUpdateUser());
            addRowDateString(tb, "Updated On", filter.getUpdateTimeMs());
            tb.row("Pipeline",
                    DocRefUtil.createSimpleDocRefString(filter.getPipeline()));

            final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
            if (tracker != null) {
                addRowDateString(tb, "Min Stream Create Ms", tracker.getMinMetaCreateMs());
                addRowDateString(tb, "Max Stream Create Ms", tracker.getMaxMetaCreateMs());
                addRowDateString(tb, "Stream Create Ms", tracker.getMetaCreateMs());
                tb.row(SafeHtmlUtil.from("Stream Create %"),
                        SafeHtmlUtil.from(tracker.getTrackerStreamCreatePercentage()));
                addRowDateString(tb, "Last Poll", tracker.getLastPollMs());
                tb.row("Last Poll Age", tracker.getLastPollAge());
                tb.row(SafeHtmlUtil.from("Last Poll Task Count"),
                        SafeHtmlUtil.from(tracker.getLastPollTaskCount()));
                tb.row("Min Stream Id", String.valueOf(tracker.getMinMetaId()));
                tb.row("Min Event Id", String.valueOf(tracker.getMinEventId()));
                tb.row(SafeHtmlUtil.from("Streams"),
                        SafeHtmlUtil.from(tracker.getMetaCount()));
                tb.row(SafeHtmlUtil.from("Events"),
                        SafeHtmlUtil.from(tracker.getEventCount()));
                tb.row("Status", tracker.getStatus());
            }
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    private void addRowDateString(final TableBuilder tb, final String label, final Long ms) {
        if (ms != null) {
            tb.row(label, dateTimeFormatter.format(ms) + " (" + ms + ")");
        }
    }
}
