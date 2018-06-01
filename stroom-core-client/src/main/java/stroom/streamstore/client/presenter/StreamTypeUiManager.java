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

package stroom.streamstore.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.security.client.event.CurrentUserChangedEvent;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.StreamTypeEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StreamTypeUiManager {
    private List<StreamTypeEntity> streamTypeList = new ArrayList<>();

    @Inject
    public StreamTypeUiManager(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        updateList(Arrays.asList(StreamTypeEntity.initialValues()));

        // We can only find out the real list when they are logged in
        eventBus.addHandler(CurrentUserChangedEvent.getType(), event -> dispatcher.exec(
                new EntityServiceFindAction<FindStreamTypeCriteria, StreamTypeEntity>(new FindStreamTypeCriteria()))
                .onSuccess(this::updateList));

    }

    private void updateList(final List<StreamTypeEntity> list) {
        streamTypeList = list;
    }

    public List<String> getRawStreamTypeList() {
        final List<String> rtn = new ArrayList<>();
        rtn.add("Raw Events");
        rtn.add("Raw Reference");
        return rtn;
    }
}
