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

package stroom.monitoring.client.presenter;

import stroom.entity.shared.BaseCriteria;

public abstract class StreamTaskStatsMonitoringPresenter<T extends BaseCriteria> {
    //
    // }
    // extends PagingDataGridPresenterWidget implements Refreshable {
    // protected T criteria;
    // protected final ClientDispatchAsync dispatcher;
    // protected final List<OldDataItem> data = new ArrayList<OldDataItem>();
    // private boolean refreshed;
    //
    // public StreamTaskStatsMonitoringPresenter(final EventBus eventBus,
    // final PagingDataGridView view,
    // final DataGridPresenter dataGridPresenter,
    // final PagingPresenter pagingPresenter,
    // final ClientDispatchAsync dispatcher) {
    // super(eventBus, view, dataGridPresenter, pagingPresenter);
    // this.dispatcher = dispatcher;
    //
    // registerHandler(pagingPresenter
    // .addPageRequestHandler(new PageRequestHandler() {
    // @Override
    // public void onPageRequest(final PageRequestEvent event) {
    // if (criteria != null) {
    // criteria.getPageRequest().setOffset(
    // event.getPageRequest().getOffset());
    // criteria.getPageRequest().setLength(
    // event.getPageRequest().getLength());
    // refresh();
    // }
    // }
    // }));
    // }
    //
    // @Override
    // public void refresh() {
    // if (!refreshed) {
    // refreshed = true;
    // getDataGridPresenter().setHeadings(getHeading());
    // }
    // }
    //
    // @Override
    // public boolean hasRefreshed() {
    // return refreshed;
    // }
    //
    // protected void setList(final BaseResultList<StreamTaskStatus> list) {
    // data.clear();
    //
    // if (list != null) {
    // long totalUnprocessed = 0;
    // long totalAssigned = 0;
    // long totalProcessing = 0;
    // long totalComplete = 0;
    // long totalFailed = 0;
    // long totalTotal = 0;
    //
    // // List all stream stats first.
    // for (final StreamTaskStatus streamStats : list) {
    // long unprocessed = streamStats
    // .getStatusCount(TaskStatus.UNPROCESSED);
    // long assigned = streamStats.getStatusCount(TaskStatus.ASSIGNED);
    // long processing = streamStats
    // .getStatusCount(TaskStatus.PROCESSING);
    // long complete = streamStats.getStatusCount(TaskStatus.COMPLETE);
    // long failed = streamStats.getStatusCount(TaskStatus.FAILED);
    // long total = unprocessed + assigned + processing + complete
    // + failed;
    //
    // totalUnprocessed += unprocessed;
    // totalAssigned += assigned;
    // totalProcessing += processing;
    // totalComplete += complete;
    // totalFailed += failed;
    // totalTotal += total;
    //
    // final List<Object> rowData = new ArrayList<Object>();
    // rowData.add(streamStats.getFeed());
    // rowData.add(ModelStringUtil.formatCsv(unprocessed));
    // rowData.add(ModelStringUtil.formatCsv(assigned));
    // rowData.add(ModelStringUtil.formatCsv(processing));
    // rowData.add(ModelStringUtil.formatCsv(complete));
    // rowData.add(ModelStringUtil.formatCsv(failed));
    // rowData.add(new HTML("<b>" + ModelStringUtil.formatCsv(total)
    // + "</b>"));
    // data.add(new OldBasicDataItem(rowData));
    // }
    //
    // // Add totals.
    // final List<Object> rowData = new ArrayList<Object>();
    // rowData.add(new HTML("<b>Total</b>"));
    // rowData.add(new HTML("<b>"
    // + ModelStringUtil.formatCsv(totalUnprocessed) + "</b>"));
    // rowData.add(new HTML("<b>"
    // + ModelStringUtil.formatCsv(totalAssigned) + "</b>"));
    // rowData.add(new HTML("<b>"
    // + ModelStringUtil.formatCsv(totalProcessing) + "</b>"));
    // rowData.add(new HTML("<b>"
    // + ModelStringUtil.formatCsv(totalComplete) + "</b>"));
    // rowData.add(new HTML("<b>" + ModelStringUtil.formatCsv(totalFailed)
    // + "</b>"));
    // rowData.add(new HTML("<b>" + ModelStringUtil.formatCsv(totalTotal)
    // + "</b>"));
    //
    // data.add(new OldBasicDataItem(rowData));
    // }
    //
    // getDataGridPresenter().setData(data);
    // }
    //
    // private List<OldDataHeading> getHeading() {
    // final List<OldDataHeading> data = new ArrayList<OldDataHeading>();
    // data.add(new OldDataHeading("Feed", 150));
    // data.add(new OldDataHeading("Unprocessed", 80));
    // data.add(new OldDataHeading("Assigned", 80));
    // data.add(new OldDataHeading("Processing", 80));
    // data.add(new OldDataHeading("Complete", 80));
    // data.add(new OldDataHeading("Failed", 80));
    // data.add(new OldDataHeading("Total", 80));
    //
    // return data;
    // }
}
