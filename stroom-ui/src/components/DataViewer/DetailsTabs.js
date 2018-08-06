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
import { Tab } from 'semantic-ui-react';

import DataDetails from './DataDetails';
import StreamDetails from './StreamDetails';

const DetailsTabs = ({ data, details }) => {
  const panes = [
    {
      menuItem: 'Data',
      render: () => (
        <Tab.Pane>
          <DataDetails data={data} />
        </Tab.Pane>
      ),
    },
    {
      menuItem: 'Details',
      render: () => (
        <Tab.Pane>
          <StreamDetails data={details} />
        </Tab.Pane>
      ),
    },
  ];
  return (
    <div className="DetailsTabs__container">
      <div className="DetailsTabs__contained">
        <Tab className="DetailsTabs__Tab" panes={panes} />
      </div>
    </div>
  );
};

DetailsTabs.propTypes = {
  data: PropTypes.object,
  details: PropTypes.object,
};

export default DetailsTabs;
