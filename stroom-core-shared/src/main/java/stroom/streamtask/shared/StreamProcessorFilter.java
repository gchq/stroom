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

import stroom.entity.shared.AuditedEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.SQLNameConstants;
import stroom.streamstore.shared.FindStreamCriteria;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlTransient;
import java.util.Comparator;

@Entity(name = "STRM_PROC_FILT")
public class StreamProcessorFilter extends AuditedEntity {
    private static final long serialVersionUID = -2478788451478923825L;

    public static final String TABLE_NAME = SQLNameConstants.STREAM + SEP + SQLNameConstants.PROCESSOR + SEP
            + SQLNameConstants.FILTER;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;

    public static final String DATA = SQLNameConstants.DATA;
    public static final String PRIORITY = SQLNameConstants.PRIORITY;
    public static final String ENABLED = SQLNameConstants.ENABLED;

    public static final String TASK_TYPE = SQLNameConstants.TASK + SQLNameConstants.TYPE_SUFFIX;

    public static final String ENTITY_TYPE = "StreamProcessorFilter";

    public static final Comparator<StreamProcessorFilter> HIGHEST_PRIORITY_FIRST_COMPARATOR = new Comparator<StreamProcessorFilter>() {
        @Override
        public int compare(final StreamProcessorFilter o1, final StreamProcessorFilter o2) {
            if (o1.getPriority() == o2.getPriority()) {
                // If priorities are the same then compare stream ids to
                // prioritise lower stream ids.
                if (o1.getStreamProcessorFilterTracker().getMinStreamId() == o2.getStreamProcessorFilterTracker()
                        .getMinStreamId()) {
                    // If stream ids are the same then compare event ids to
                    // prioritise lower event ids.
                    return Long.compare(o1.getStreamProcessorFilterTracker().getMinEventId(),
                            o2.getStreamProcessorFilterTracker().getMinEventId());
                }

                return Long.compare(o1.getStreamProcessorFilterTracker().getMinStreamId(),
                        o2.getStreamProcessorFilterTracker().getMinStreamId());
            }

            // Highest Priority is important.
            return Integer.compare(o2.getPriority(), o1.getPriority());
        }
    };

    private String data;
    private StreamProcessor streamProcessor;
    private StreamProcessorFilterTracker streamProcessorFilterTracker;
    private FindStreamCriteria findStreamCriteria;

    /**
     * The higher the number the higher the priority. So 1 is LOW, 10 is medium,
     * 20 is high.
     */
    private int priority = 10;

    private boolean enabled;

    public StreamProcessorFilter() {
        // Default constructor necessary for GWT serialisation.
    }

    @Column(name = DATA, nullable = false, length = Integer.MAX_VALUE)
    @Lob
    @ExternalFile
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @Column(name = PRIORITY, nullable = false)
    public int getPriority() {
        return priority;
    }

    public void setPriority(final int priority) {
        this.priority = priority;
    }

    public boolean isHigherPriority(final StreamProcessorFilter other) {
        return HIGHEST_PRIORITY_FIRST_COMPARATOR.compare(this, other) < 0;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = StreamProcessor.FOREIGN_KEY)
    public StreamProcessor getStreamProcessor() {
        return streamProcessor;
    }

    public void setStreamProcessor(final StreamProcessor streamProcessor) {
        this.streamProcessor = streamProcessor;
    }

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = StreamProcessorFilterTracker.FOREIGN_KEY)
    public StreamProcessorFilterTracker getStreamProcessorFilterTracker() {
        return streamProcessorFilterTracker;
    }

    public void setStreamProcessorFilterTracker(final StreamProcessorFilterTracker streamProcessorFilterTracker) {
        this.streamProcessorFilterTracker = streamProcessorFilterTracker;
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    @Transient
    @XmlTransient
    public FindStreamCriteria getFindStreamCriteria() {
        return findStreamCriteria;
    }

    public void setFindStreamCriteria(final FindStreamCriteria findStreamCriteria) {
        this.findStreamCriteria = findStreamCriteria;
    }

    @Column(name = ENABLED, nullable = false)
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "[" + priority + "] - " + findStreamCriteria.toString();
    }
}
