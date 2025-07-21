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

import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This has been put here as import export API is not Serializable.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
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
    private boolean singletonDoc = false;

    @JsonCreator
    public ImportState(@JsonProperty("docRef") final DocRef docRef,
                       @JsonProperty("sourcePath") final String sourcePath,
                       @JsonProperty("destPath") final String destPath,
                       @JsonProperty("action") final boolean action,
                       @JsonProperty("messageList") final List<Message> messageList,
                       @JsonProperty("updatedFieldList") final List<String> updatedFieldList,
                       @JsonProperty("state") final State state,
                       @JsonProperty("singletonDoc") final boolean singletonDoc) {
        this.docRef = docRef;
        this.sourcePath = sourcePath;
        this.destPath = destPath;
        this.action = action;
        this.messageList = messageList;
        this.updatedFieldList = updatedFieldList;
        this.state = state;
        this.singletonDoc = singletonDoc;
    }

    public ImportState(final DocRef docRef, final String sourcePath) {
        this.docRef = docRef;
        this.sourcePath = sourcePath;
        this.messageList = new ArrayList<>();
        this.updatedFieldList = new ArrayList<>();
        this.singletonDoc = false;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(final String sourcePath) {
        this.sourcePath = sourcePath;
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

    public void setAction(final boolean action) {
        this.action = action;
    }

    public List<Message> getMessageList() {
        return messageList;
    }

    public void addMessage(final Severity severity, final String message) {
        messageList.add(new Message(severity, message));
    }

    /**
     * @return True if the docRef represented by this {@link ImportState} is a singleton,
     * i.e. there is only one instance of its type, and it does not appear in the explorer
     * tree.
     */
    public boolean isSingletonDoc() {
        return singletonDoc;
    }

    /**
     * @param singletonDoc Set to true if the docRef represented by this {@link ImportState}
     *                     is a singleton, i.e. there is only one instance of its type,
     *                     and it does not appear in the explorer tree. If true, will also
     *                     set sourcePath and destPath to null.
     */
    public void setSingletonDoc(final boolean singletonDoc) {
        this.singletonDoc = singletonDoc;
        if (singletonDoc) {
            this.sourcePath = null;
            this.destPath = null;
        }
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

    public State getState() {
        return state;
    }

    public void setState(final State state) {
        this.state = state;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

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


    // --------------------------------------------------------------------------------


    public enum State implements HasDisplayValue {
        NEW("New"),
        UPDATE("Update"),
        EQUAL("Equal"),
        IGNORE("Ignore");

        private static final Map<String, State> DISPLAY_VALUE_TO_ENUM_MAP = Arrays.stream(values())
                .collect(Collectors.toMap(State::getDisplayValue, Function.identity()));

        private final String displayValue;

        State(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        public static State fromDisplayValue(final String displayValue) {
            return DISPLAY_VALUE_TO_ENUM_MAP.get(displayValue);
        }
    }
}
