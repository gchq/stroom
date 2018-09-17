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
import PropTypes from 'prop-types';

import { compose, withHandlers, withProps } from 'recompose';
import { connect } from 'react-redux';

import { actionCreators, defaultListingState } from './redux/deleteDocRefReducer';
import { deleteDocuments } from 'components/FolderExplorer/explorerClient';
import { ThemedConfirm } from 'components/ThemedModal';

const { completeDocRefDelete } = actionCreators;

const enhance = compose(
  connect(
    ({ folderExplorer: { deleteDocRef } }, { listingId }) => ({
      ...(deleteDocRef[listingId] || defaultListingState),
    }),
    { completeDocRefDelete, deleteDocuments },
  ),
  withHandlers({
    onConfirm: ({ deleteDocuments, uuids }) => () => deleteDocuments(uuids),
    onCancel: ({ completeDocRefDelete, listingId }) => () => completeDocRefDelete(listingId),
  }),
  withProps(({ isDeleting, uuids }) => ({
    isOpen: isDeleting,
    question: `Delete these doc refs? ${JSON.stringify(uuids)}?`,
  })),
);

const DeleteDocRefDialog = enhance(ThemedConfirm);

DeleteDocRefDialog.propTypes = {
  listingId: PropTypes.string.isRequired,
};

export default DeleteDocRefDialog;
