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

package stroom.entity.shared;

import stroom.util.shared.HasDisplayValue;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;
import stroom.util.shared.SharedObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This has been put here as import export API is not Serializable.
 */
public class ImportState implements SharedObject {
    public enum ImportMode {
        CREATE_CONFIRMATION, ACTION_CONFIRMATION, IGNORE_CONFIRMATION
    }

    public enum State implements HasDisplayValue {
        NEW("New"), UPDATE("Update"), EQUAL("Equal");

        private final String displayValue;

        State(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    private static final long serialVersionUID = -5451928033766595954L;

    private DocRef docRef;
    private String sourcePath;
    private String destPath;

    private boolean action;
    private List<Message> messageList = new ArrayList<>();
    private List<String> updatedFieldList = new ArrayList<>();
    private State state;

    public ImportState() {
        // Default constructor for GWT serialisation.
    }

    public ImportState(final DocRef docRef, final String sourcePath) {
        this.docRef = docRef;
        this.sourcePath = sourcePath;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getDestPath() {
        return destPath;
    }

    public void setDestPath(final String destPath) {
        this.destPath = destPath;
    }

    public boolean isAction() {
        return action;
    }

    public void setAction(boolean action) {
        this.action = action;
    }

    public List<Message> getMessageList() {
        return messageList;
    }

    public void addMessage(final Severity severity, final String message) {
        messageList.add(new Message(severity, message));
    }

    public Severity getSeverity() {
        Severity severity = Severity.INFO;
        for (final Message message : messageList) {
            if (message.getSeverity().greaterThan(severity)) {
                severity = message.getSeverity();
            }
        }
        return severity;
    }

    public List<String> getUpdatedFieldList() {
        return updatedFieldList;
    }

    public State getState() {
        return state;
    }

    public void setState(final State state) {
        this.state = state;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ImportState that = (ImportState) o;

        return docRef.equals(that.docRef);
    }

    @Override
    public int hashCode() {
        return docRef.hashCode();
    }

    @Override
    public String toString() {
        return docRef.toString();
    }
}
