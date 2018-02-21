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

package stroom.streamstore.server;

import stroom.streamstore.shared.FindStreamCriteria;
import stroom.util.task.ServerTask;

import java.nio.file.Path;

public class StreamDownloadTask extends ServerTask<StreamDownloadResult> {
    private FindStreamCriteria criteria;
    private Path file;
    private StreamDownloadSettings settings;

    public StreamDownloadTask() {
    }

    public StreamDownloadTask(final String userToken, final FindStreamCriteria criteria,
                              final Path file, final StreamDownloadSettings settings) {
        super(null, userToken);
        this.criteria = criteria;
        this.file = file;
        this.settings = settings;
    }

    public FindStreamCriteria getCriteria() {
        return criteria;
    }

    public Path getFile() {
        return file;
    }

    public StreamDownloadSettings getSettings() {
        return settings;
    }
}
