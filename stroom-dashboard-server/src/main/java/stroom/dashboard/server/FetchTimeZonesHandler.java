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

package stroom.dashboard.server;

import org.joda.time.DateTimeZone;
import org.springframework.context.annotation.Scope;
import stroom.dashboard.shared.FetchTimeZonesAction;
import stroom.dashboard.shared.TimeZoneData;
import stroom.security.Insecure;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@TaskHandlerBean(task = FetchTimeZonesAction.class)
@Scope(StroomScope.TASK)
@Insecure
public class FetchTimeZonesHandler extends AbstractTaskHandler<FetchTimeZonesAction, TimeZoneData> {
    @Override
    public TimeZoneData exec(final FetchTimeZonesAction action) {
        final List<String> ids = new ArrayList<>(DateTimeZone.getAvailableIDs());
        Collections.sort(ids);

        return new TimeZoneData(ids);
    }
}
