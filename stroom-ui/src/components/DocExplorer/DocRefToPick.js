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

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { actionCreators as docExplorerActionCreators } from './redux/explorerTreeReducer';

const { docRefSelected } = docExplorerActionCreators;

const enhance = compose(connect(
  (state, props) => ({
    // state
    explorer: state.explorerTree.explorers[props.explorerId],
  }),
  {
    docRefSelected,
  },
));

const DocRefToPick = ({
  explorerId, explorer, docRef, docRefSelected,
}) => {
  const onSingleClick = () => docRefSelected(explorerId, docRef);

  const isSelected = explorer.isSelected[docRef.uuid];

  let className = '';
  if (isSelected) {
    className += ' doc-ref__selected';
  }

  return (
    <div className={className} onClick={onSingleClick}>
      <span>
        <img className="doc-ref__icon" alt="X" src={require(`./images/${docRef.type}.svg`)} />
        {docRef.name}
      </span>
    </div>
  );
};

DocRefToPick.propTypes = {
  explorerId: PropTypes.string.isRequired,
  docRef: PropTypes.object.isRequired,
};

export default enhance(DocRefToPick);
