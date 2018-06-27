import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { compose, withState } from 'recompose';
import { Tab, Menu } from 'semantic-ui-react';

import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer';
import DocRefEditor from './DocRefEditor';

const { docRefClosed } = docExplorerActionCreators;

const enhance = compose(connect(
  (state, props) => ({
    openDocRefs: state.explorerTree.openDocRefs,
  }),
  { docRefClosed },
));

const ContentTabs = enhance(({ openDocRefs, docRefClosed }) => {
  const panes = [];

  // Add all the open doc refs
  openDocRefs
    .map(docRef => ({
      menuItem: (
        <Menu.Item key={docRef.uuid}>
          {docRef.name}
          <button className="close-btn" onClick={() => docRefClosed(docRef)}>
            x
          </button>
        </Menu.Item>
      ),
      pane: (
        <Tab.Pane key={docRef.uuid}>
          <DocRefEditor docRef={docRef} />
        </Tab.Pane>
      ),
    }))
    .forEach(p => panes.push(p));

  return <Tab renderActiveOnly={false} panes={panes} />;
});

ContentTabs.propTypes = {};

export default ContentTabs;
