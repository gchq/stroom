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

package stroom.importexport.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * This has been put here as import export API is not Serializable.
 */
@JsonInclude(Include.NON_NULL)
public class ImportState {
    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private String sourcePath;
    @JsonProperty
    private String destPath;
    @JsonProperty
    private boolean action;
    @JsonProperty
    private final List<Message> messageList;
    @JsonProperty
    private final List<String> updatedFieldList;
    @JsonProperty
    private State state;
    @JsonProperty
    private Long enableTime;
    @JsonProperty
    private Boolean enable;

    @JsonCreator
    public ImportState(@JsonProperty("docRef") final DocRef docRef,
                       @JsonProperty("sourcePath") final String sourcePath,
                       @JsonProperty("destPath") final String destPath,
                       @JsonProperty("action") final boolean action,
                       @JsonProperty("messageList") final List<Message> messageList,
                       @JsonProperty("updatedFieldList") final List<String> updatedFieldList,
                       @JsonProperty("enable") final Boolean enable,
                       @JsonProperty("enableTime") final Long enableTime,
                       @JsonProperty("state") final State state) {
        this.docRef = docRef;
        this.sourcePath = sourcePath;
        this.destPath = destPath;
        this.action = action;
        this.messageList = messageList;
        this.updatedFieldList = updatedFieldList;
        this.enableTime = enableTime;
        this.enable = enable;
        this.state = state;
    }

    public ImportState(final DocRef docRef, final String sourcePath) {
        this.docRef = docRef;
        this.sourcePath = sourcePath;
        this.messageList = new ArrayList<>();
        this.updatedFieldList = new ArrayList<>();
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

    public boolean ok(final ImportMode importMode) {
        return importMode == ImportMode.IGNORE_CONFIRMATION
                || (importMode == ImportMode.ACTION_CONFIRMATION && action);
    }

    public boolean isAction() {
        return action;
    }

    public void setAction(final boolean action) {
        this.action = action;
    }

    public List<Message> getMessageList() {
        return messageList;
    }

    public void addMessage(final Severity severity, final String message) {
        messageList.add(new Message(severity, message));
    }

    @JsonIgnore
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

    public Long getEnableTime(){
        return enableTime;
    }

    public Boolean getEnable() { return enable; }

    public void setEnable(Boolean enable) {this.enable = enable;}

    public void setEnableTime(Long enableTime){
        this.enableTime = enableTime;
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
}
