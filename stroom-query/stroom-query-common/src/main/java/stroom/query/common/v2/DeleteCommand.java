package stroom.query.common.v2;

import stroom.query.api.v2.TimeFilter;

public class DeleteCommand implements LmdbQueueItem {

    private final Key parentKey;
    private final TimeFilter timeFilter;

    public DeleteCommand(final Key parentKey, final TimeFilter timeFilter) {
        this.parentKey = parentKey;
        this.timeFilter = timeFilter;
    }

    public Key getParentKey() {
        return parentKey;
    }

    public TimeFilter getTimeFilter() {
        return timeFilter;
    }
}
