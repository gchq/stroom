/*
 * Copyright 2018 Crown Copyright
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

package stroom.dictionary.shared;

import stroom.entity.shared.Action;
import stroom.util.shared.ResourceGeneration;

public class DownloadDictionaryAction extends Action<ResourceGeneration> {
    private static final long serialVersionUID = -3560107233301674555L;

    private String uuid;

    public DownloadDictionaryAction() {
    }

    public DownloadDictionaryAction(final String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    @Override
    public String getTaskName() {
        return "Download dictionary";
    }
}
