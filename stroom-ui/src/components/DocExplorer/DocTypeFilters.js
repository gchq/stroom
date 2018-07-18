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
import { compose, withProps } from 'recompose';
import { Form, Checkbox } from 'semantic-ui-react';

import withDocRefTypes from './withDocRefTypes';
import { actionCreators } from './redux';

const { typeFilterChanged } = actionCreators;

const ALL_SELECT_STATE = {
  ALL: 0,
  NONE: 1,
  INDETERMINATE: 2,
};

const enhance = compose(
  withDocRefTypes,
  connect(
    ({ docExplorer }, { explorerId }) => ({
      typeFilters: docExplorer.explorerTree.explorers[explorerId].typeFilters,
      docRefTypes: docExplorer.explorerTree.docRefTypes.filter(d => d !== 'Folder'),
    }),
    { typeFilterChanged },
  ),
  withProps(({ docRefTypes, typeFilters }) => {
    let allSelectState;
    if (typeFilters.length === 0) {
      allSelectState = ALL_SELECT_STATE.NONE;
    } else if (typeFilters.length === docRefTypes.length) {
      allSelectState = ALL_SELECT_STATE.ALL;
    } else {
      allSelectState = ALL_SELECT_STATE.INDETERMINATE;
    }
    return {
      allSelectState,
    };
  }),
);

const DocTypeFilters = ({
  explorerId,
  typeFilters,
  docRefTypes,
  typeFilterChanged,
  allSelectState,
}) => (
  <div>
    <Form.Field>
      <img className="doc-ref__icon" alt="X" src={require('./images/System.svg')} />
      <Checkbox
        label="All"
        indeterminate={allSelectState === ALL_SELECT_STATE.INDETERMINATE}
        checked={allSelectState === ALL_SELECT_STATE.ALL}
        onChange={(e, { checked }) =>
          docRefTypes.forEach(docRefType => typeFilterChanged(explorerId, docRefType, checked))
        }
      />;
    </Form.Field>
    {docRefTypes
      .map(docRefType => ({ docRefType, isSelected: typeFilters.includes(docRefType) }))
      .map(({ docRefType, isSelected }) => (
        <Form.Field key={docRefType}>
          <img className="doc-ref__icon" alt="X" src={require(`./images/${docRefType}.svg`)} />
          <Checkbox
            label={docRefType}
            checked={isSelected}
            onChange={() => typeFilterChanged(explorerId, docRefType, !isSelected)}
          />
        </Form.Field>
      ))}
  </div>
);

DocTypeFilters.propTypes = {
  explorerId: PropTypes.string.isRequired,
};

export default enhance(DocTypeFilters);
