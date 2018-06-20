import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { compose, withState } from 'recompose';
import { Tab, Menu } from 'semantic-ui-react';

import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer';
import DocRefEditor from './DocRefEditor';

const { docRefClosed } = docExplorerActionCreators;

const withWelcomeTab = withState('welcomeIsOpen', 'setWelcomeIsOpen', true);

const ContentTabs = ({
  openDocRefs, setWelcomeIsOpen, welcomeIsOpen, docRefClosed,
}) => {
  const panes = [];

  if (welcomeIsOpen) {
    panes.push({
      menuItem: {
        key: 'welcome',
        content: 'Welcome',
      },
      pane: (
        <Tab.Pane key="welcome">Stroom is designed to receive data from multiple systems.</Tab.Pane>
      ),
    });
  }

  // Add all the open doc refs
  openDocRefs
    .map(docRef => ({
      menuItem: (
        <Menu.Item>
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
};

ContentTabs.propTypes = {
  openDocRefs: PropTypes.array.isRequired,

  // Redux actions
  docRefClosed: PropTypes.func.isRequired,

  // with Welcome
  setWelcomeIsOpen: PropTypes.func.isRequired,
  welcomeIsOpen: PropTypes.bool.isRequired,
};

export default compose(
  connect(
    (state, props) => ({
      openDocRefs: state.explorerTree.openDocRefs,
    }),
    { docRefClosed },
  ),
  withWelcomeTab,
)(ContentTabs);
