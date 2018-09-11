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
import { compose, withProps, withHandlers } from 'recompose';

import DocRefImage from 'components/DocRefImage';
import withDocRefTypes from './withDocRefTypes';

const ALL_SELECT_STATE = {
  ALL: 0,
  NONE: 1,
  INDETERMINATE: 2,
};

const enhance = compose(
  withDocRefTypes,
  connect(({ docRefTypes }) => ({
    docRefTypes,
  })),
  withProps(({ docRefTypes, value, onChange }) => {
    let allSelectState;
    if (value.length === 0) {
      allSelectState = ALL_SELECT_STATE.NONE;
    } else if (value.length === docRefTypes.length) {
      allSelectState = ALL_SELECT_STATE.ALL;
    } else {
      allSelectState = ALL_SELECT_STATE.INDETERMINATE;
    }
    return {
      allSelectState,
    };
  }),
  withHandlers({
    onAllCheckboxChanged: ({ allSelectState, onChange, docRefTypes }) => () => {
      switch (allSelectState) {
        case ALL_SELECT_STATE.ALL:
        case ALL_SELECT_STATE.INDETERMINATE:
          onChange([]);
          break;
        case ALL_SELECT_STATE.NONE:
          onChange(docRefTypes);
          break;
      }
    },
  }),
);

const DocTypeFilters = ({
  docRefTypes, onChange, value, allSelectState, onAllCheckboxChanged,
}) => (
  <React.Fragment>
    <div>
      <DocRefImage size="small" docRefType="System" />
      <label>All</label>
      <input
        type="checkbox"
        checked={allSelectState === ALL_SELECT_STATE.ALL}
        onChange={onAllCheckboxChanged}
      />;
    </div>
    {docRefTypes
      .map(docRefType => ({ docRefType, isSelected: value.includes(docRefType) }))
      .map(({ docRefType, isSelected }) => (
        <div key={docRefType}>
          <DocRefImage size="small" docRefType={docRefType} />
          <label>{docRefType}</label>
          <input
            type="checkbox"
            checked={isSelected}
            onChange={() => {
              if (isSelected) {
                onChange(value.filter(v => v !== docRefType));
              } else {
                onChange(value.concat([docRefType]));
              }
            }}
          />
        </div>
      ))}
  </React.Fragment>
);

const EnhancedDocTypeFilters = enhance(DocTypeFilters);

EnhancedDocTypeFilters.propTypes = {
  value: PropTypes.arrayOf(PropTypes.string).isRequired,
  onChange: PropTypes.func.isRequired,
};

EnhancedDocTypeFilters.defaultProps = {
  value: [],
  onChange: v => console.log('Not implemented onChange, value ignored', v),
};

export default EnhancedDocTypeFilters;
