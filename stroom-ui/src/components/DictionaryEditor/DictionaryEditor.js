import React from 'react';

import { compose, lifecycle, renderComponent, branch, withHandlers, withProps } from 'recompose';
import { withRouter } from 'react-router-dom';
import { connect } from 'react-redux';

import Tooltip from 'components/Tooltip';

import Loader from 'components/Loader';
import DocRefImage from 'components/DocRefImage';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import { fetchDictionary } from './dictionaryResourceClient';
import { saveDictionary } from './dictionaryResourceClient';
import ThemedAceEditor from 'components/ThemedAceEditor';

import { actionCreators } from './redux';

const { dictionaryUpdated } = actionCreators;

const enhance = compose(
  withRouter,
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
  withProps(({ dictionaryState: { isDirty, isSaving } }) => ({
    saveDisabled: !isDirty,
    saveCaption: isSaving ? 'Saving...' : isDirty ? 'Save' : 'Saved',
  })),
  withHandlers({
    openDocRef: ({ history }) => d => history.push(`/s/doc/${d.type}/${d.uuid}`),
    onDataChange: ({ dictionaryUuid, dictionaryUpdated }) => ({ target: { value } }) =>
      dictionaryUpdated(dictionaryUuid, { data: value }),
    onClickSave: ({ saveDictionary, dictionaryUuid }) => e => saveDictionary(dictionaryUuid),
  }),
);

const DictionaryEditor = ({
  dictionaryUuid,
  dictionaryState: { dictionary },
  dictionaryUpdated,
  saveDictionary,
  openDocRef,
  onDataChange,
  onClickSave,
  saveDisabled,
  saveCaption,
}) => (
  <div className="DictionaryEditor">
    <DocRefImage docRefType="XSLT" className="DictionaryEditor__headerIcon" />
    <h3 className="DictionaryEditor__headerTitle">{dictionaryUuid}</h3>

    <DocRefBreadcrumb
      className="DictionaryEditor__breadcrumb"
      docRefUuid={dictionaryUuid}
      openDocRef={openDocRef}
    />

    <div className="DictionaryEditor__actionButtons">
      <button disabled={saveDisabled} title="Save Dictionary" onClick={onClickSave}>
        {saveCaption}
      </button>
    </div>
    <div className="DictionaryEditor__main">
      <textarea value={dictionary.data} onChange={onDataChange} />
    </div>
  </div>
);

export default enhance(DictionaryEditor);
