/*
 * Copyright 2018 Crown Copyright
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

import React from 'react';
import { connect } from 'react-redux';
import { compose, withHandlers, withProps } from 'recompose';
import { Pagination, Dropdown } from 'semantic-ui-react';

import { actionCreators } from '../redux';
import { fetchTrackers } from '../streamTasksResourceClient';

const { changePage, updatePageSize } = actionCreators;

const enhance = compose(
  connect(
    ({ processing: { pageSize, pageOffset, numberOfPages } }) => ({
      pageSize,
      pageOffset,
      numberOfPages,
    }),
    {
      fetchTrackers,
      changePage,
      updatePageSize,
    },
  ),
  withHandlers({
    onHandlePageChange: ({ changePage, fetchTrackers }) => (data) => {
      changePage(data.activePage - 1);
      fetchTrackers();
    },
    onHandlePageSizeChange: ({ updatePageSize, fetchTrackers }) => (event, data) => {
      updatePageSize(data.value);
      fetchTrackers();
    },
  }),
  withProps(({ pageOffset, numberOfPages }) => ({
    pageOptions: [
      {
        text: 10,
        value: 10,
      },
      {
        text: 20,
        value: 20,
      },
      {
        text: 30,
        value: 30,
      },
      {
        text: 40,
        value: 40,
      },
      {
        text: 50,
        value: 50,
      },
      {
        text: 100,
        value: 100,
      },
    ],
    activePage: pageOffset + 1,
    totalPages: numberOfPages || 1,
  })),
);

const ProcessingPagination = ({
  activePage,
  totalPages,
  pageOptions,
  pageSize,
  onHandlePageChange,
  onHandlePageSizeChange,
}) => (
  <div className="pagination__container">
    <Pagination
      className="flat border"
      activePage={activePage}
      totalPages={totalPages}
      firstItem={null}
      lastItem={null}
      size="tiny"
      onPageChange={(event, data) => onHandlePageChange(data)}
    />
    <div className="pagination__text">Show</div>
    <Dropdown
      fluid
      selection
      value={pageSize}
      options={pageOptions}
      onChange={onHandlePageSizeChange}
    />
  </div>
);

export default enhance(ProcessingPagination);
