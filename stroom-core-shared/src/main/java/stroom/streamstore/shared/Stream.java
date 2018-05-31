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

package stroom.streamstore.shared;

import stroom.docref.SharedObject;
import stroom.streamtask.shared.StreamProcessor;

public interface Stream extends SharedObject {
    long getId();

    String getFeedName();

    String getStreamTypeName();

    String getPipelineName();

//    FeedEntity getFeed();
//
//    StreamType getStreamType();

    Long getParentStreamId();

    Long getStreamTaskId();

    StreamProcessor getStreamProcessor();

    StreamStatus getStatus();

    Long getStatusMs();

    long getCreateMs();

    Long getEffectiveMs();
}
