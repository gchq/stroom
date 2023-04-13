package stroom.dashboard.client.main;

import stroom.query.api.v2.TimeRange;

public interface DashboardContext extends HasParams {

    TimeRange getTimeRange();
}
