import React from 'react';
import { compose, lifecycle, renderComponent, branch, withHandlers, withProps } from 'recompose';
import { connect } from 'react-redux';

import Loader from 'components/Loader';
import DocRefEditor from 'components/DocRefEditor';
import { fetchDictionary } from './dictionaryResourceClient';
import { saveDictionary } from './dictionaryResourceClient';
import { actionCreators } from './redux';

const { dictionaryUpdated } = actionCreators;

const enhance = compose(
  connect(
    ({ dictionaryEditor }, { dictionaryUuid }) => ({
      dictionaryState: dictionaryEditor[dictionaryUuid],
    }),
    {
      fetchDictionary,
      dictionaryUpdated,
      saveDictionary,
    },
  ),
  lifecycle({
    componentDidMount() {
      const { fetchDictionary, dictionaryUuid } = this.props;

      fetchDictionary(dictionaryUuid);
    },
  }),
  branch(
    ({ dictionaryState }) => !dictionaryState,
    renderComponent(() => <Loader active>Loading Dictionary</Loader>),
  ),
  withHandlers({
    onDataChange: ({ dictionaryUuid, dictionaryUpdated }) => ({ target: { value } }) =>
      dictionaryUpdated(dictionaryUuid, { data: value }),
    onClickSave: ({ saveDictionary, dictionaryUuid }) => e => saveDictionary(dictionaryUuid),
  }),
  withProps(({ dictionaryState: { isDirty, isSaving }, onClickSave }) => ({
    actionBarItems: [
      {
        icon: 'save',
        disabled: !(isDirty || isSaving),
        title: isSaving ? 'Saving...' : isDirty ? 'Save' : 'Saved',
        onClick: onClickSave,
      },
    ],
  })),
);

const DictionaryEditor = ({
  dictionaryUuid,
  dictionaryState: { dictionary },
  openDocRef,
  onDataChange,
  actionBarItems,
}) => (
  <DocRefEditor
    docRef={{
      type: 'Dictionary',
      uuid: dictionaryUuid,
    }}
    actionBarItems={actionBarItems}
  >
    <textarea value={dictionary.data} onChange={onDataChange} />
  </DocRefEditor>
);

export default enhance(DictionaryEditor);
