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

package stroom.statistics.impl.sql.shared;

public class StatisticsDataSourceFieldChangeRequest {
    private StatisticsDataSourceData oldStatisticsDataSourceData;
    private StatisticsDataSourceData newStatisticsDataSourceData;

    public StatisticsDataSourceFieldChangeRequest() {
        // Default constructor necessary for GWT serialisation.
    }

    public StatisticsDataSourceFieldChangeRequest(final StatisticsDataSourceData oldStatisticsDataSourceData,
                                                  final StatisticsDataSourceData newStatisticsDataSourceData) {
        this.oldStatisticsDataSourceData = oldStatisticsDataSourceData;
        this.newStatisticsDataSourceData = newStatisticsDataSourceData;
    }

    public StatisticsDataSourceData getOldStatisticsDataSourceData() {
        return oldStatisticsDataSourceData;
    }

    public void setOldStatisticsDataSourceData(final StatisticsDataSourceData oldStatisticsDataSourceData) {
        this.oldStatisticsDataSourceData = oldStatisticsDataSourceData;
    }

    public StatisticsDataSourceData getNewStatisticsDataSourceData() {
        return newStatisticsDataSourceData;
    }

    public void setNewStatisticsDataSourceData(final StatisticsDataSourceData newStatisticsDataSourceData) {
        this.newStatisticsDataSourceData = newStatisticsDataSourceData;
    }
}
