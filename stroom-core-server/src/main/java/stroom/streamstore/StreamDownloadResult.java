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

package stroom.streamstore;

import stroom.docref.SharedObject;

public class StreamDownloadResult implements SharedObject {
    private static final long serialVersionUID = -5012089569913789389L;

    private long recordsWritten = 0;
    private boolean hitMaxFileSize = false;
    private boolean hitMaxFileParts = false;

    public StreamDownloadResult() {
    }

    public long getRecordsWritten() {
        return recordsWritten;
    }

    public void setRecordsWritten(long recordsWritten) {
        this.recordsWritten = recordsWritten;
    }

    public void incrementRecordsWritten() {
        recordsWritten++;
    }

    public boolean isHitMaxFileSize() {
        return hitMaxFileSize;
    }

    public void setHitMaxFileSize(boolean hitMaxFileSize) {
        this.hitMaxFileSize = hitMaxFileSize;
    }

    public boolean isHitMaxFileParts() {
        return hitMaxFileParts;
    }

    public void setHitMaxFileParts(boolean hitMaxFileParts) {
        this.hitMaxFileParts = hitMaxFileParts;
    }

}
