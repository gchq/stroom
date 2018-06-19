import React, { Component } from 'react';
import PropTypes from 'prop-types';
import loremIpsum from 'lorem-ipsum';
import {
  Sidebar,
  Input,
  Segment,
  Button,
  Menu,
  Image,
  Icon,
  Header,
  Grid,
  Divider,
  Container,
  Checkbox,
} from 'semantic-ui-react';
import { compose, withState } from 'recompose';

const enhance = compose(withState('activeItem', 'setActiveItem', 'home'));

const HorizontalPanel = enhance(({
  title, headerMenuItems, content, activeItem, setActiveItem, onClose,
}) => (
  <div className="horizontal-panel__container">
    <Grid>
      <Grid.Column width="4">
        <Header as="h2">{title}</Header>
      </Grid.Column>
      <Grid.Column width="12">
        <Menu secondary>
          <Menu.Menu position="right">
            {headerMenuItems}
            <Menu.Item>
              <Button icon="close" onClick={() => onClose()} />
            </Menu.Item>
          </Menu.Menu>
        </Menu>
      </Grid.Column>
    </Grid>
    <Divider />
    <div className="horizontal-panel__content">{content}</div>
  </div>
));

HorizontalPanel.propTypes = {
  content: PropTypes.object.isRequired,
  title: PropTypes.string.isRequired,
  headerMenuItems: PropTypes.array,
  onClose: PropTypes.func.isRequired,
};

export default HorizontalPanel;
