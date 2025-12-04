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

package stroom.data.store.impl;


import stroom.util.shared.Message;

import java.util.ArrayList;
import java.util.List;

public class DataDownloadResult {

    private static final long serialVersionUID = -5012089569913789389L;

    private long recordsWritten = 0;
    private boolean hitMaxFileSize = false;
    private boolean hitMaxFileParts = false;
    private List<Message> messageList = null;

    public DataDownloadResult() {
    }

    public long getRecordsWritten() {
        return recordsWritten;
    }

    public void setRecordsWritten(final long recordsWritten) {
        this.recordsWritten = recordsWritten;
    }

    public void incrementRecordsWritten() {
        recordsWritten++;
    }

    public boolean isHitMaxFileSize() {
        return hitMaxFileSize;
    }

    public void setHitMaxFileSize(final boolean hitMaxFileSize) {
        this.hitMaxFileSize = hitMaxFileSize;
    }

    public boolean isHitMaxFileParts() {
        return hitMaxFileParts;
    }

    public void setHitMaxFileParts(final boolean hitMaxFileParts) {
        this.hitMaxFileParts = hitMaxFileParts;
    }

    public List<Message> getMessageList() {
        return messageList;
    }

    public void addMessage(final Message message) {
        if (messageList == null) {
            messageList = new ArrayList<>();
        }
        messageList.add(message);
    }
}
