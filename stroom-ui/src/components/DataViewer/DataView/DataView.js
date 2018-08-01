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
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, lifecycle, branch, renderComponent } from 'recompose';
import { Loader } from 'semantic-ui-react';
import { path } from 'ramda';
// eslint-disable-next-line
import brace from 'brace';
import 'brace/mode/xml';
import 'brace/theme/github';
import 'brace/keybinding/vim';

import ErrorView from './ErrorView';
import EventView from './EventView';

const enhance = compose(
  connect((state, props) => {
    const dataView = state.dataViewers[props.dataViewerId];
    if (dataView !== undefined) {
      return {
        data: dataView.dataForSelectedRow,
      };
    }
    return { data: undefined };
  }, {}),
  branch(
    ({ dataViewerId }) => !dataViewerId,
    renderComponent(() => <Loader active>Loading data</Loader>),
  ),
);

const DataView = ({ data }) => {
  console.log({ data });
  const streamType = path(['streamType', 'path'], data);
  const eventData = path(['data'], data);
  const markerData = path(['markers'], data);
  let content;
  if (streamType === 'ERROR') return <ErrorView errors={markerData} />;
  else if (streamType === 'RAW_EVENTS') return <EventView events={eventData} />;
  else if (streamType === 'EVENTS') return <EventView events={eventData} />;
  return <div>TODO</div>;
};

DataView.propTypes = {
  dataViewerId: PropTypes.string.isRequired,
  data: PropTypes.object.isRequired,
};

export default enhance(DataView);
