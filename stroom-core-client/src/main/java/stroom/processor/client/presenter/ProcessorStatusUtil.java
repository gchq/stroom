package stroom.processor.client.presenter;

import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.ProcessorListRow;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.customdatebox.client.MomentJs;

class ProcessorStatusUtil {

    public static String getValue(final ProcessorListRow row) {
        String status = null;
        if (row instanceof ProcessorFilterRow) {
            final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
            final ProcessorFilterTracker tracker = processorFilterRow
                    .getProcessorFilter()
                    .getProcessorFilterTracker();
            if (tracker != null) {
                if (!GwtNullSafe.isBlankString(tracker.getMessage())) {
                    status = GwtNullSafe.getOrElseGet(
                            tracker.getStatus(),
                            status2 -> status2.getDisplayValue() + ": " + tracker.getMessage(),
                            tracker::getMessage);
                } else if (!ProcessorFilterTrackerStatus.CREATED.equals(tracker.getStatus())) {
                    status = tracker.getStatus().getDisplayValue();
                } else if (tracker.getLastPollTaskCount() != null && tracker.getLastPollTaskCount() == 0) {
                    status = "Up to date";
                } else if (tracker.getMetaCreateMs() != null) {
                    final long age = System.currentTimeMillis() - tracker.getMetaCreateMs();
                    status = MomentJs.humanise(age) + " behind";
                }
            }
        }
        return status;
    }
}
