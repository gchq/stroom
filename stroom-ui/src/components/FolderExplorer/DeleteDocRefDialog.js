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

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Button, Header } from 'semantic-ui-react';

import { actionCreators } from './redux';
import { deleteDocuments } from 'components/FolderExplorer/explorerClient';
import ThemedModal from 'components/ThemedModal';

const { completeDocRefDelete } = actionCreators;

const enhance = compose(connect(
  ({
    folderExplorer: {
      deleteDocRef: { isDeleting, uuids },
    },
  }) => ({
    isDeleting,
    uuids,
  }),
  { completeDocRefDelete, deleteDocuments },
));

const DeleteDocRefDialog = ({
  isDeleting, uuids, completeDocRefDelete, deleteDocuments,
}) => (
  <ThemedModal
    open={isDeleting}
    header={
      <Header
        className="header"
        icon="trash"
        content="Are you sure about deleting these Doc Refs?"
      />
    }
    content={JSON.stringify(uuids)}
    actions={
      <React.Fragment>
        <Button negative onClick={completeDocRefDelete}>
          Cancel
        </Button>
        <Button
          positive
          onClick={() => deleteDocuments(uuids)}
          labelPosition="right"
          icon="checkmark"
          content="Choose"
        />
      </React.Fragment>
    }
  />
);

export default enhance(DeleteDocRefDialog);
