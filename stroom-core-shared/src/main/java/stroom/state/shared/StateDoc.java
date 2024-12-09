/*
 * Copyright 2017 Crown Copyright
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

package stroom.state.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.time.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description("Defines a place to store state")
@JsonPropertyOrder({
        "uuid",
        "name",
        "uniqueName",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "scyllaDbRef",
        "stateType",
        "condense",
        "condenseAge",
        "condenseTimeUnit",
        "retainForever",
        "retainAge",
        "retainTimeUnit"

})
@JsonInclude(Include.NON_NULL)
public class StateDoc extends AbstractDoc {

    public static final String DOCUMENT_TYPE = "StateStore";
    public static final SvgImage ICON = SvgImage.DOCUMENT_STATE_STORE;

    /**
     * Reference to the `scyllaDb` containing common connection properties
     */
    @JsonProperty
    private DocRef scyllaDbRef;
    @JsonProperty
    private String description;
    @JsonProperty
    private StateType stateType;
    @JsonProperty
    private boolean condense;
    @JsonProperty
    private int condenseAge;
    @JsonProperty
    private TimeUnit condenseTimeUnit;
    @JsonProperty
    private boolean retainForever;
    @JsonProperty
    private int retainAge;
    @JsonProperty
    private TimeUnit retainTimeUnit;

    public StateDoc() {
    }

    @JsonCreator
    public StateDoc(@JsonProperty("uuid") final String uuid,
                    @JsonProperty("name") final String name,
                    @JsonProperty("uniqueName") final String uniqueName,
                    @JsonProperty("version") final String version,
                    @JsonProperty("createTimeMs") final Long createTimeMs,
                    @JsonProperty("updateTimeMs") final Long updateTimeMs,
                    @JsonProperty("createUser") final String createUser,
                    @JsonProperty("updateUser") final String updateUser,
                    @JsonProperty("description") final String description,
                    @JsonProperty("scyllaDbRef") final DocRef scyllaDbRef,
                    @JsonProperty("stateType") final StateType stateType,
                    @JsonProperty("condense") final boolean condense,
                    @JsonProperty("condenseAge") final int condenseAge,
                    @JsonProperty("condenseTimeUnit") final TimeUnit condenseTimeUnit,
                    @JsonProperty("retainForever") final boolean retainForever,
                    @JsonProperty("retainAge") final int retainAge,
                    @JsonProperty("retainTimeUnit") final TimeUnit retainTimeUnit) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.scyllaDbRef = scyllaDbRef;
        this.stateType = stateType;
        this.condense = condense;
        this.condenseAge = condenseAge;
        this.condenseTimeUnit = condenseTimeUnit;
        this.retainForever = retainForever;
        this.retainAge = retainAge;
        this.retainTimeUnit = retainTimeUnit;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public DocRef getScyllaDbRef() {
        return scyllaDbRef;
    }

    public void setScyllaDbRef(final DocRef scyllaDbRef) {
        this.scyllaDbRef = scyllaDbRef;
    }

    public StateType getStateType() {
        return stateType;
    }

    public void setStateType(final StateType stateType) {
        this.stateType = stateType;
    }

    public boolean isCondense() {
        return condense;
    }

    public void setCondense(final boolean condense) {
        this.condense = condense;
    }

    public int getCondenseAge() {
        return condenseAge;
    }

    public void setCondenseAge(final int condenseAge) {
        this.condenseAge = condenseAge;
    }

    public TimeUnit getCondenseTimeUnit() {
        return condenseTimeUnit;
    }

    public void setCondenseTimeUnit(final TimeUnit condenseTimeUnit) {
        this.condenseTimeUnit = condenseTimeUnit;
    }

    public boolean isRetainForever() {
        return retainForever;
    }

    public void setRetainForever(final boolean retainForever) {
        this.retainForever = retainForever;
    }

    public int getRetainAge() {
        return retainAge;
    }

    public void setRetainAge(final int retainAge) {
        this.retainAge = retainAge;
    }

    public TimeUnit getRetainTimeUnit() {
        return retainTimeUnit;
    }

    public void setRetainTimeUnit(final TimeUnit retainTimeUnit) {
        this.retainTimeUnit = retainTimeUnit;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final StateDoc doc = (StateDoc) o;
        return condense == doc.condense &&
               condenseAge == doc.condenseAge &&
               retainForever == doc.retainForever &&
               retainAge == doc.retainAge &&
               Objects.equals(scyllaDbRef, doc.scyllaDbRef) &&
               Objects.equals(description, doc.description) &&
               stateType == doc.stateType &&
               condenseTimeUnit == doc.condenseTimeUnit &&
               retainTimeUnit == doc.retainTimeUnit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                scyllaDbRef,
                description,
                stateType,
                condense,
                condenseAge,
                condenseTimeUnit,
                retainForever,
                retainAge,
                retainTimeUnit);
    }

    @Override
    public String toString() {
        return "StateDoc{" +
               "scyllaDbRef=" + scyllaDbRef +
               ", description='" + description + '\'' +
               ", stateType=" + stateType +
               ", condense=" + condense +
               ", condenseAge=" + condenseAge +
               ", condenseTimeUnit=" + condenseTimeUnit +
               ", retainForever=" + retainForever +
               ", retainAge=" + retainAge +
               ", retainTimeUnit=" + retainTimeUnit +
               '}';
    }
}
