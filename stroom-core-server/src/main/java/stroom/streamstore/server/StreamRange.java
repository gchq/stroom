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

package stroom.streamstore.server;

import stroom.entity.shared.Period;
import stroom.entity.shared.Range;
import stroom.util.date.DateUtil;

/**
 * Class to hold a range of stream matches (based on a repo path).
 */
public class StreamRange extends Range<Long> {
    private static final long serialVersionUID = -6549930015935794660L;
    private static final long DAY_MS = 1000 * 60 * 60 * 24;
    private String streamTypePath;
    private Period createPeriod;
    private boolean invalidPath;

    public StreamRange(final String repoPath) {
        final String[] parts = repoPath.split("/");

        if (parts.length > 0 && parts[0].length() > 0) {
            streamTypePath = parts[0];
        }
        if (parts.length >= 4) {
            try {
                final String stroomTime = parts[1] + "-" + parts[2] + "-" + parts[3] + "T00:00:00.000Z";
                final long startDate = DateUtil.parseNormalDateTimeString(stroomTime);
                final long endDate = startDate + DAY_MS;
                createPeriod = new Period(startDate, endDate);
            } catch (final Exception ex) {
                // Not a stream path
                invalidPath = true;
            }
        }

        final StringBuilder numberPart = new StringBuilder();
        for (int i = 4; i < parts.length; i++) {
            numberPart.append(parts[i]);
        }

        if (parts.length == 4) {
            init(1L, 1000L);
        }

        if (numberPart.length() > 0) {
            try {
                final long dirNumber = Long.valueOf(numberPart.toString());

                // E.g. 001/110 would contain numbers 1,110,000 to 1,110,999
                // 001/111 would contain numbers 1,111,000 to 1,111,999
                final long fromId = dirNumber * 1000L;
                init(fromId, fromId + 1000L);

            } catch (final Exception ex) {
                // Not a stream path
                invalidPath = true;
            }
        }
    }

    public Period getCreatePeriod() {
        return createPeriod;
    }

    public boolean isInvalidPath() {
        return invalidPath;
    }

    public boolean isFileLocation() {
        return isBounded();
    }

    public String getStreamTypePath() {
        return streamTypePath;
    }

}
