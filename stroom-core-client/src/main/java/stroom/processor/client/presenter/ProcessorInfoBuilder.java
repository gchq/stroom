package stroom.processor.client.presenter;

import stroom.data.client.presenter.OpenLinkUtil;
import stroom.data.client.presenter.OpenLinkUtil.LinkType;
import stroom.docstore.shared.DocRefUtil;
import stroom.preferences.client.DateTimeFormatter;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorRow;
import stroom.processor.shared.ProcessorType;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

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
            if (ProcessorType.STREAMING_ANALYTIC.equals(processor.getProcessorType())) {
                tb.row("Analytic Rule",
                        DocRefUtil.createSimpleDocRefString(processor.getPipeline()));
            } else {
                tb.row("Pipeline",
                        DocRefUtil.createSimpleDocRefString(processor.getPipeline()));
            }

        } else if (row instanceof ProcessorFilterRow) {
            final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
            final ProcessorFilter filter = processorFilterRow.getProcessorFilter();

            if (filter.isReprocess()) {
                tb.row(TableCell.header("Stream Processor Filter (reprocessing)", 2));
            } else {
                tb.row(TableCell.header("Stream Processor Filter", 2));
            }

            if (filter.getMinMetaCreateTimeMs() != null ||
                filter.getMaxMetaCreateTimeMs() != null) {
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
            tb.row(SafeHtmlUtil.from("Run As User"), SafeHtmlUtil.from(
                    NullSafe.get(filter.getRunAsUser(), (UserRef ref) -> ref.toDisplayString())));
            tb.row("Created By", filter.getCreateUser());
            addRowDateString(tb, "Created On", filter.getCreateTimeMs());
            tb.row("Updated By", filter.getUpdateUser());
            addRowDateString(tb, "Updated On", filter.getUpdateTimeMs());

            tb.row("Export", filter.isExport() ? "True" : "False");

            if (ProcessorType.STREAMING_ANALYTIC.equals(filter.getProcessorType())) {
                tb.row("Analytic Rule",
                        DocRefUtil.createSimpleDocRefString(filter.getPipeline()));
            } else {
                tb.row("Pipeline",
                        DocRefUtil.createSimpleDocRefString(filter.getPipeline()));
            }

            tb.row("Reprocess",
                    filter.isReprocess()
                            ? "True"
                            : "False");
            tb.row("Max Concurrent Tasks",
                    filter.getMaxProcessingTasks() == 0
                            ? "Unlimited"
                            : String.valueOf(filter.getMaxProcessingTasks()));

            final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
            if (tracker != null) {
                addRowDateString(tb, "Min Stream Create Time", tracker.getMinMetaCreateMs());
                addRowDateString(tb, "Max Stream Create Time", tracker.getMaxMetaCreateMs());
                addRowDateString(tb, "Stream Create Time", tracker.getMetaCreateMs());
                addRowDateString(tb, "Last Poll", tracker.getLastPollMs());
                tb.row("Last Poll Age", tracker.getLastPollAge());
                tb.row(SafeHtmlUtil.from("Last Poll Task Count"),
                        SafeHtmlUtil.from(tracker.getLastPollTaskCount()));

                tb.row(SafeHtmlUtils.fromString("Min Stream Id"),
                        OpenLinkUtil.render(String.valueOf(tracker.getMinMetaId()), LinkType.STREAM));

                tb.row("Min Event Id", String.valueOf(tracker.getMinEventId()));
                tb.row(SafeHtmlUtil.from("Total Tasks Created"),
                        SafeHtmlUtil.from(tracker.getMetaCount()));
                if (tracker.getEventCount() != null) {
                    tb.row(SafeHtmlUtil.from("Total Events"),
                            SafeHtmlUtil.from(tracker.getEventCount()));
                }
                tb.row("Status", ProcessorStatusUtil.getValue(row));
            }
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tb::write, Attribute.className("infoTable"));
        return htmlBuilder.toSafeHtml();
    }

    private void addRowDateString(final TableBuilder tb, final String label, final Long ms) {
        if (ms != null) {
            tb.row(label, dateTimeFormatter.formatWithDuration(ms));
        }
    }
}
