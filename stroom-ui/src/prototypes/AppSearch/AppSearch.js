import React from 'react';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Button, Header, Icon, Modal, Menu } from 'semantic-ui-react';

import { actionCreators } from './redux';

const { appSearchOpened, appSearchClosed } = actionCreators;

const enhance = compose(connect(
  (state, props) => ({
    isOpen: state.appSearch.isOpen,
  }),
  { appSearchClosed },
));

const AppSearch = ({ isOpen, appSearchClosed }) => (
  <Modal open={isOpen} onClose={appSearchClosed} basic size="small" dimmer="inverted">
    <Header icon="search" content="App Search" />
    <Modal.Content>
      <Menu vertical />
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={appSearchClosed} inverted>
        <Icon name="checkmark" /> Close
      </Button>
    </Modal.Actions>
  </Modal>
);

export default enhance(AppSearch);
