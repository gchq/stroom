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
import stroom.streamstore.shared.StreamType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StreamTypeUiManager {
    private List<StreamType> streamTypeList = new ArrayList<StreamType>();

    @Inject
    public StreamTypeUiManager(final EventBus eventBus, final ClientDispatchAsync dispatcher) {
        updateList(Arrays.asList(StreamType.initialValues()));

        // We can only find out the real list when they are logged in
        eventBus.addHandler(CurrentUserChangedEvent.getType(), event -> dispatcher.exec(
                new EntityServiceFindAction<FindStreamTypeCriteria, StreamType>(new FindStreamTypeCriteria()))
                .onSuccess(this::updateList));

    }

    private void updateList(final List<StreamType> list) {
        streamTypeList = list;
    }

    static class StreamTypeCompare implements Comparator<StreamType> {
        @Override
        public int compare(final StreamType o1, final StreamType o2) {
            if (o1.isStreamTypeRaw() == o2.isStreamTypeRaw()) {
                if (o1.isStreamTypeError()) {
                    return +1;
                }
                if (o2.isStreamTypeError()) {
                    return -1;
                }
                return o1.getName().compareTo(o2.getName());
            }
            if (o1.isStreamTypeRaw()) {
                return -1;
            } else {
                return +1;
            }

        }
    }

    public List<StreamType> getRawStreamTypeList() {
        final List<StreamType> rtn = new ArrayList<StreamType>();
        for (final StreamType streamType : streamTypeList) {
            if (streamType.isStreamTypeRaw()) {
                rtn.add(streamType);
            }
        }
        Collections.sort(rtn, new StreamTypeCompare());
        return rtn;
    }

    public List<StreamType> getProcessedStreamTypeList() {
        final List<StreamType> rtn = new ArrayList<StreamType>();
        for (final StreamType streamType : streamTypeList) {
            if (streamType.isStreamTypeProcessed()) {
                rtn.add(streamType);
            }
        }
        Collections.sort(rtn, new StreamTypeCompare());
        return rtn;
    }

    public List<StreamType> getRootStreamTypeList() {
        final List<StreamType> rtnList = new ArrayList<StreamType>();
        for (final StreamType streamType : streamTypeList) {
            if (!streamType.isStreamTypeChild()) {
                rtnList.add(streamType);
            }
        }
        Collections.sort(rtnList, new StreamTypeCompare());
        return rtnList;
    }

}
