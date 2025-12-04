/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.feed.client;

import com.google.gwt.user.client.rpc.RemoteService;

public interface DataUploadService extends RemoteService {

    // No additional methods to add.
    String STATUS = "Status";
    String SUCCESS = "Success";
    String FAILED = "Failed";
    String MESSAGE = "Message";
    String SIZE = "Size";
}
