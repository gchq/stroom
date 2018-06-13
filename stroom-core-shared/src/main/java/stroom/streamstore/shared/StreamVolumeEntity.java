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

package stroom.streamstore.shared;

import stroom.entity.shared.BaseEntityBig;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

/**
 * Link table for which streams live on which volumes.
 */
@Entity(name = "FS_STRM_VOL")
public class StreamVolumeEntity extends BaseEntityBig {
    public static final String TABLE_NAME = SQLNameConstants.STREAM + SEP + SQLNameConstants.VOLUME;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String ENTITY_TYPE = "StreamVolume";
    public static final String LAST_ACCESS_MS = SQLNameConstants.LAST + SEP + SQLNameConstants.ACCESS
            + SQLNameConstants.MS_SUFFIX;
    private static final long serialVersionUID = 6729492408680929025L;
    private Long volumeId;
    private Long streamId;

    public StreamVolumeEntity() {
        // Default constructor necessary for GWT serialisation.
    }

    @Column(name = "FK_VOL_ID", nullable = false)
    public Long getVolumeId() {
        return volumeId;
    }

    public void setVolumeId(final Long volume) {
        this.volumeId = volumeId;
    }

    @Column(name = "FK_STRM_ID", nullable = false)
    public Long getStream() {
        return streamId;
    }

    public void setStream(final Long streamId) {
        this.streamId = streamId;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        sb.append(", streamId=");
        sb.append(streamId);
        sb.append(", volumeId=");
        sb.append(volumeId);
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
