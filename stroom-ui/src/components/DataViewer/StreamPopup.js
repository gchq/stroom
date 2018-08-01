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
import { Icon, Popup } from 'semantic-ui-react';

const StreamPopup = ({ streamData }) => {
  const eventIcon = <Icon color="blue" name="file" />;
  const warningIcon = <Icon color="orange" name="warning circle" />;
  const errorIcon = <Icon color="red" name="warning circle" />;

  let icon,
    title;
  if (streamData.stream.streamType.name === 'Events') {
    title = 'Events';
    icon = eventIcon;
  } else if (streamData.stream.streamType.name === 'Error') {
    title = 'Error';
    icon = warningIcon;
  }

  const position = 'right center';

  console.log({ streamData });
  return (
    <Popup
      trigger={icon}
      content={
        <div>
          <h4>{title}</h4>
          <p> Stream ID: {streamData.stream.id}</p>
        </div>
      }
      position="right center"
    />
  );
};

StreamPopup.propTypes = {
  streamData: PropTypes.object.isRequired,
};

export default StreamPopup;
