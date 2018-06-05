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
import stroom.node.shared.VolumeEntity;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

/**
 * Link table for which streams live on which volumes.
 */
@Entity(name = "STRM_VOL")
public class StreamVolumeEntity extends BaseEntityBig {
    public static final String TABLE_NAME = SQLNameConstants.STREAM + SEP + SQLNameConstants.VOLUME;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String ENTITY_TYPE = "StreamVolume";
    public static final String LAST_ACCESS_MS = SQLNameConstants.LAST + SEP + SQLNameConstants.ACCESS
            + SQLNameConstants.MS_SUFFIX;
    private static final long serialVersionUID = 6729492408680929025L;
    private VolumeEntity volume;
    private StreamEntity stream;

    public StreamVolumeEntity() {
        // Default constructor necessary for GWT serialisation.
    }

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = VolumeEntity.FOREIGN_KEY)
    public VolumeEntity getVolume() {
        return volume;
    }

    public void setVolume(final VolumeEntity volume) {
        this.volume = volume;
    }

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = StreamEntity.FOREIGN_KEY)
    public StreamEntity getStream() {
        return stream;
    }

    public void setStream(final StreamEntity stream) {
        this.stream = stream;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        if (getStream() != null) {
            sb.append(", streamId=");
            sb.append(getStream().getId());
        }
        if (getVolume() != null) {
            sb.append(", volumeId=");
            sb.append(getVolume().getId());
        }
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
