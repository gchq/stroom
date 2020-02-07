/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.client.table;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.TimeZoneData;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;

import java.util.List;

@Singleton
public class TimeZones {
    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);

    private String timeZone;
    private List<String> ids;

    @Inject
    public TimeZones(final RestFactory restFactory) {
        try {
            timeZone = getIntlTimeZone();
        } catch (final RuntimeException e) {
        }

        final Rest<TimeZoneData> rest = restFactory.create();
        rest
                .onSuccess(result -> ids = result.getIds())
                .call(DASHBOARD_RESOURCE)
                .fetchTimeZones();
    }

    public String getTimeZone() {
        return timeZone;
    }

    public List<String> getIds() {
        return ids;
    }

    /**
     * This javascript call attempts to get the time zone, e.g. 'Europe/London'
     * using the ECMAScript Internationalisation API Specification
     *
     * @return The browsers time zone, e.g. 'Europe/London' or
     * 'Australia/Sydney'.
     */
    private native String getIntlTimeZone()
    /*-{
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }-*/;
}
