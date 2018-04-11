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

package stroom.refdata;

import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.xml.event.EventList;

/**
 * Stores and retrieves a list of XML events that are found in reference data
 * for specified map and key names.
 */
public interface MapStore {
    EventList getEvents(String mapName, String keyName);

    StoredErrorReceiver getErrorReceiver();
}
