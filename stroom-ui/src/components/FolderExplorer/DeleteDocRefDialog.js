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
import { Header } from 'semantic-ui-react';

import { actionCreators } from './redux';
import { deleteDocuments } from 'components/FolderExplorer/explorerClient';
import ThemedModal from 'components/ThemedModal';
import DialogActionButtons from './DialogActionButtons';

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
      isOpen={isDeleting}
      header={
        <Header
          className="header"
          icon="trash"
          content="Are you sure about deleting these Doc Refs?"
        />
      }
      content={JSON.stringify(uuids)}
      actions={
        <DialogActionButtons
          onCancel={completeDocRefDelete}
          onChoose={() => deleteDocuments(uuids)}
        />
      }
    />
  );

export default enhance(DeleteDocRefDialog);
