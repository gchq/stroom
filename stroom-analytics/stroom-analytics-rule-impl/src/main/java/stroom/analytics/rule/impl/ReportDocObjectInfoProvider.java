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

package stroom.analytics.rule.impl;

import stroom.analytics.shared.ReportDoc;
import stroom.event.logging.api.ObjectInfoProvider;

import event.logging.BaseObject;
import event.logging.OtherObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ReportDocObjectInfoProvider implements ObjectInfoProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportDocObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final Object obj) {
        final ReportDoc alertRule = (ReportDoc) obj;
        final OtherObject.Builder<Void> builder = OtherObject.builder()
                .withType(alertRule.getType())
                .withId(alertRule.getUuid())
                .withName(alertRule.getName())
                .withDescription(alertRule.getDescription());

//        try {
//            builder.addData(EventLoggingUtil.createData("AlertRule", alertRule.getAlertRule()));
//        } catch (final RuntimeException e) {
//            LOGGER.error("Unable to add unknown but useful data!", e);
//        }

        return builder.build();
    }

    @Override
    public String getObjectType(final Object object) {
        return object.getClass().getSimpleName();
    }
}
