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

package stroom.search.solr.client.presenter;

import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.List;

public class SolrIndexFieldDataProvider<R> extends AsyncDataProvider<R> {

    private List<R> list;
    private Range requestedRange;

    @Override
    protected void onRangeChanged(final HasData<R> display) {
        fetch(display.getVisibleRange());
    }

    public void setList(final List<R> list) {
        this.list = list;
    }

    private void fetch(final Range range) {
        if (range != null) {
            requestedRange = range;
            if (list != null) {
                final List<R> subList = new ArrayList<>();
                for (int i = range.getStart(); i < (range.getStart() + range.getLength()) && i < list.size(); i++) {
                    subList.add(list.get(i));
                }
                updateRowData(range.getStart(), subList);
                updateRowCount(list.size(), true);
            }
        }
    }

    public void refresh() {
        fetch(requestedRange);
    }
}
