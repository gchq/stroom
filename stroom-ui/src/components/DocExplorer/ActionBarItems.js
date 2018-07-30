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
import { compose, branch, renderComponent } from 'recompose';
import { connect } from 'react-redux';

import { Input, Button, Popup } from 'semantic-ui-react';

import { actionCreators } from './redux';

import withExplorerTree from './withExplorerTree';
import DocTypeFilters from './DocTypeFilters';

const { searchTermUpdated } = actionCreators;

const enhance = compose(
  withExplorerTree,
  connect(
    (state, props) => {
      console.log({ state });
      return {
        explorer: state.docExplorer.explorerTree.explorers[props.explorerId],
      };
    },
    { searchTermUpdated },
  ),
  branch(({ explorer }) => !explorer, renderComponent(() => <div />)),
);

const ActionBarItems = ({ explorerId, searchTermUpdated, explorer }) => (
  <div className="DocExplorer__ActionBarItems__container">
    <Input
      icon="search"
      placeholder="Search..."
      value={explorer.searchTerm}
      onChange={e => searchTermUpdated(explorerId, e.target.value)}
    />
    <Popup trigger={<Button icon="filter" />} flowing hoverable>
      <DocTypeFilters explorerId={explorerId} />
    </Popup>
  </div>
);

ActionBarItems.propTypes = {
  explorerId: PropTypes.string.isRequired,
};

export default enhance(ActionBarItems);
