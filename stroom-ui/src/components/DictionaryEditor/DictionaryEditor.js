import React from 'react';

import { compose, lifecycle, renderComponent, branch, withHandlers } from 'recompose';
import { withRouter } from 'react-router-dom';
import { connect } from 'react-redux';
import { Loader, Button, Grid, Header } from 'semantic-ui-react';

import Tooltip from 'components/Tooltip';

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
  withHandlers({
    openDocRef: ({ history }) => d => history.push(`/s/doc/${d.type}/${d.uuid}`),
    onDataChange: ({ dictionaryUuid, dictionaryUpdated }) => ({ target: { value } }) =>
      dictionaryUpdated(dictionaryUuid, { data: value }),
  }),
);

const DictionaryEditor = ({
  dictionaryUuid,
  dictionaryState: { isDirty, isSaving, dictionary },
  dictionaryUpdated,
  saveDictionary,
  openDocRef,
  onDataChange,
}) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={12}>
        <Header as="h3">
          <DocRefImage docRefType="XSLT" />
          <Header.Content>{dictionaryUuid}</Header.Content>
          <Header.Subheader>
            <DocRefBreadcrumb docRefUuid={dictionaryUuid} openDocRef={openDocRef} />
          </Header.Subheader>
        </Header>
      </Grid.Column>
      <Grid.Column width={4}>
        <Tooltip
          trigger={
            <Button
              floated="right"
              circular
              icon="save"
              color={isDirty ? 'blue' : undefined}
              loading={isSaving}
              onClick={() => {
                if (dictionaryUuid) saveDictionary(dictionaryUuid);
              }}
            />
          }
          content={isDirty ? 'Save changes' : 'Changes saved'}
        />
      </Grid.Column>
    </Grid>
    <div className="dictionary-editor">
      <textarea value={dictionary.data} onChange={onDataChange} />
    </div>
  </React.Fragment>
);

export default enhance(DictionaryEditor);
