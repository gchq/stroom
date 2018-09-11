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
import { compose, branch, renderNothing, renderComponent, withProps } from 'recompose';

import Loader from 'components/Loader';
import ThemedModal from 'components/ThemedModal';
import { actionCreators } from './redux';
import IconHeader from 'components/IconHeader';

const { docRefInfoClosed } = actionCreators;

const enhance = compose(
  connect(
    ({ docRefInfo: { isOpen, docRefInfo } }, props) => ({
      isOpen,
      docRefInfo,
    }),
    { docRefInfoClosed },
  ),
  branch(({ isOpen }) => !isOpen, renderNothing),
  branch(
    ({ docRefInfo }) => !docRefInfo,
    renderComponent(() => <Loader message="Awaiting DocRef info..." />),
  ),
  withProps(({ docRefInfo: { createTime, updateTime } }) => ({
    formattedCreateTime: new Date(createTime).toLocaleString('en-GB', { timeZone: 'UTC' }),
    formattedUpdateTime: new Date(updateTime).toLocaleString('en-GB', { timeZone: 'UTC' }),
  })),
);

const doNothing = () => {};

const DocRefInfoModal = ({
  isOpen,
  docRefInfo,
  docRefInfoClosed,
  formattedCreateTime,
  formattedUpdateTime,
}) => (
  <ThemedModal
    isOpen={isOpen}
    onClose={docRefInfoClosed}
    header={<IconHeader icon="info" text="Document Information" />}
    content={
      <form>
        <div className="DocRefInfo__formRow">
          <span>
            <label>Type</label>
            <input type="text" value={docRefInfo.docRef.type} onChange={doNothing} />
          </span>
          <span>
            <label>UUID</label>
            <input type="text" value={docRefInfo.docRef.uuid} onChange={doNothing} />
          </span>
          <span>
            <label>Name</label>
            <input type="text" value={docRefInfo.docRef.name} onChange={doNothing} />
          </span>
        </div>
        <div className="DocRefInfo__formRow">
          <span>
            <label>Created by</label>
            <input type="text" value={docRefInfo.createUser} onChange={doNothing} />
          </span>
          <span>
            <label>at</label>
            <input type="text" value={formattedCreateTime} onChange={doNothing} />
          </span>
        </div>
        <div className="DocRefInfo__formRow">
          <span>
            <label>Updated by</label>
            <input type="text" value={docRefInfo.updateUser} onChange={doNothing} />
          </span>
          <span>
            <label>at</label>
            <input type="text" value={formattedUpdateTime} onChange={doNothing} />
          </span>
        </div>
        <div className="DocRefInfo__formRow">
          <span>
            <label>Other Info</label>
            <input
              label="Other Info"
              type="text"
              value={docRefInfo.otherInfo}
              onChange={doNothing}
            />
          </span>
        </div>
      </form>
    }
    actions={<button onClick={docRefInfoClosed}>Close</button>}
  />
);

export default enhance(DocRefInfoModal);
