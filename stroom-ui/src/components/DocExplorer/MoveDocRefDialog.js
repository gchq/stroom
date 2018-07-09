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

import { Modal } from 'semantic-ui-react';

import { actionCreators } from './redux';
import DocPicker from './DocPicker/DocPicker';
import PermissionInheritancePicker from './PermissionInheritancePicker';

const { completeDocRefMoves } = actionCreators;

const enhance = compose(connect(
  (state, props) => ({
    docRefs: state.moveDocRef[props.explorerId],
  }),
  { completeDocRefMoves },
));

const MoveDocRefDialog = ({ explorerId, docRefs, completeDocRefMoves }) => (
  <Modal open={docRefs.length > 0}>
    <Modal.Header>Select a Doc Ref</Modal.Header>
    <Modal.Content scrolling>
      <DocPicker explorerId={`move-${explorerId}`} typeFilter="Folder" foldersOnly />
      <PermissionInheritancePicker pickerId={explorerId} />
    </Modal.Content>
  </Modal>
);

MoveDocRefDialog.propTypes = {
  explorerId: PropTypes.string.isRequired,
};

export default enhance(MoveDocRefDialog);
