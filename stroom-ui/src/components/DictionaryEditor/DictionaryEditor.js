import React from 'react';
import { compose, lifecycle, renderComponent, branch, withHandlers, withProps } from 'recompose';
import { withRouter } from 'react-router-dom';
import { connect } from 'react-redux';

import Loader from 'components/Loader';
import { DocRefIconHeader } from 'components/IconHeader';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import { fetchDictionary } from './dictionaryResourceClient';
import { saveDictionary } from './dictionaryResourceClient';
import { actionCreators } from './redux';
import Button from 'components/Button';

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
    <DocRefIconHeader
      docRefType="Dictionary"
      className="DictionaryEditor__header"
      text={dictionaryUuid}
    />

    <DocRefBreadcrumb
      className="DictionaryEditor__breadcrumb"
      docRefUuid={dictionaryUuid}
      openDocRef={openDocRef}
    />

    <div className="DictionaryEditor__actionButtons">
      <Button
        circular
        icon="save"
        disabled={saveDisabled}
        title="Save Dictionary"
        onClick={onClickSave}
      />
    </div>
    <div className="DictionaryEditor__main">
      <textarea value={dictionary.data} onChange={onDataChange} />
    </div>
  </div>
);

export default enhance(DictionaryEditor);
