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

package stroom.streamtask.shared;

import stroom.entity.shared.BaseEntityBig;
import stroom.entity.shared.SQLNameConstants;
import stroom.node.shared.Node;

import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

@MappedSuperclass
public abstract class TaskBasedEntity extends BaseEntityBig {
    public static final String START_TIME_MS = "START_TIME" + SQLNameConstants.MS_SUFFIX;
    public static final String END_TIME_MS = "END_TIME" + SQLNameConstants.MS_SUFFIX;
    public static final String STATUS = SQLNameConstants.STATUS;
    public static final String STATUS_MS = SQLNameConstants.STATUS + SQLNameConstants.MS_SUFFIX;
    public static final String CREATE_MS = SQLNameConstants.CREATE + SQLNameConstants.MS_SUFFIX;
    private static final long serialVersionUID = -6752797140242673318L;
    private Node node;

    private Long createMs;
    private Long statusMs;
    private Long startTimeMs;
    private Long endTimeMs;
    private byte pstatus = TaskStatus.UNPROCESSED.getPrimitiveValue();

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = Node.FOREIGN_KEY)
    public Node getNode() {
        return node;
    }

    public void setNode(final Node node) {
        this.node = node;
    }

    @Column(name = STATUS, nullable = false)
    public byte getPstatus() {
        return pstatus;
    }

    public void setPstatus(final byte pstatus) {
        this.pstatus = pstatus;
    }

    @Transient
    public TaskStatus getStatus() {
        return TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pstatus);
    }

    public void setStatus(final TaskStatus processStreamStatus) {
        pstatus = processStreamStatus.getPrimitiveValue();
    }

    @Column(name = START_TIME_MS, columnDefinition = BIGINT_UNSIGNED)
    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public void setStartTimeMs(final Long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    @Column(name = CREATE_MS, columnDefinition = BIGINT_UNSIGNED)
    public Long getCreateMs() {
        return createMs;
    }

    public void setCreateMs(final Long createMs) {
        this.createMs = createMs;
    }

    @Column(name = STATUS_MS, columnDefinition = BIGINT_UNSIGNED)
    public Long getStatusMs() {
        return statusMs;
    }

    public void setStatusMs(final Long statusMs) {
        this.statusMs = statusMs;
    }

    @Column(name = END_TIME_MS, columnDefinition = BIGINT_UNSIGNED)
    public Long getEndTimeMs() {
        return endTimeMs;
    }

    public void setEndTimeMs(final Long endTimeMs) {
        this.endTimeMs = endTimeMs;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        sb.append(", status=");
        sb.append(getStatus());
    }
}
