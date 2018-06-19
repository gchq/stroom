import React from 'react';
import PropTypes from 'prop-types';
import { Button, Menu, Header, Grid, Divider } from 'semantic-ui-react';
import { compose, withState } from 'recompose';

const enhance = compose(withState('activeItem', 'setActiveItem', 'home'));

const HorizontalPanel = enhance(({
  title,
  headerMenuItems,
  content,
  activeItem,
  setActiveItem,
  onClose,
  titleColumns,
  menuColumns,
  headerSize,
}) => (
  <div className="horizontal-panel__container">
    <Grid>
      <Grid.Column width={titleColumns || 4}>
        <Header as={headerSize || 'h2'}>{title}</Header>
      </Grid.Column>
      <Grid.Column width={menuColumns || 12}>
        <Menu secondary>
          <Menu.Menu position="right">
            {headerMenuItems}
            <Menu.Item className="horizontal-panel_close-button">
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
  title: PropTypes.object.isRequired,
  headerMenuItems: PropTypes.array,
  onClose: PropTypes.func.isRequired,
  titleColumns: PropTypes.number,
  menuColumns: PropTypes.number,
  headerSize: PropTypes.string,
};

export default HorizontalPanel;
