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
import { compose, withHandlers } from 'recompose';
import { Pagination } from 'semantic-ui-react';

import { actionCreators } from '../redux';
import { fetchTrackers } from '../streamTasksResourceClient';

const { changePage } = actionCreators;

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
    },
  ),
  withHandlers({
    onHandlePageChange: ({ changePage, fetchTrackers }) => (data) => {
      changePage(data.activePage - 1);
      fetchTrackers();
    },
  }),
);

const ProcessingPagination = ({ pageOffset, numberOfPages, onHandlePageChange }) => (
  <Pagination
    activePage={pageOffset + 1}
    totalPages={numberOfPages || 1}
    firstItem={null}
    lastItem={null}
    size="tiny"
    onPageChange={(event, data) => onHandlePageChange(data)}
  />
);

export default enhance(ProcessingPagination);
