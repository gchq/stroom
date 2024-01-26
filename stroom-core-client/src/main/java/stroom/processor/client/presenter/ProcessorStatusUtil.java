package stroom.processor.client.presenter;

import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.ProcessorListRow;
import stroom.widget.customdatebox.client.ClientDurationUtil;

class ProcessorStatusUtil {

    public static String getValue(final ProcessorListRow row) {
        String status = null;
        if (row instanceof ProcessorFilterRow) {
            final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
            final ProcessorFilterTracker tracker = processorFilterRow
                    .getProcessorFilter()
                    .getProcessorFilterTracker();
            if (tracker != null) {
                if (tracker.getMessage() != null && tracker.getMessage().trim().length() > 0) {
                    if (tracker.getStatus() != null) {
                        status = tracker.getStatus().getDisplayValue() + ": " + tracker.getMessage();
                    } else {
                        status = tracker.getMessage();
                    }
                } else if (!ProcessorFilterTrackerStatus.CREATED.equals(tracker.getStatus())) {
                    status = tracker.getStatus().getDisplayValue();
                } else if (tracker.getLastPollTaskCount() != null && tracker.getLastPollTaskCount() == 0) {
                    status = "Up to date";
                } else if (tracker.getMetaCreateMs() != null) {
                    final long age = System.currentTimeMillis() - tracker.getMetaCreateMs();
                    status = ClientDurationUtil.humanise(age) + " behind";
                }
            }
        }
        return status;
    }
}
