import React from 'react';
import PropTypes from 'prop-types';
import { Button, Menu, Grid } from 'semantic-ui-react';
import { compose, withState, lifecycle } from 'recompose';
import Mousetrap from 'mousetrap';

const enhance = compose(
  withState('activeItem', 'setActiveItem', 'home'),
  lifecycle({
    componentDidMount() {
      const { onClose } = this.props;
      Mousetrap.bind('esc', () => onClose());
    },
    componentWillMount() {
      Mousetrap.unbind('esc');
    },
  }),
);

const HorizontalPanel = ({
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
    <Grid className="horizontal-panel_header-container">
      <Grid.Column width={titleColumns || 4} className="HorizontalPanel_title__container">
        <strong>{title}</strong>
      </Grid.Column>
      <Grid.Column width={menuColumns || 12}>
        <Menu secondary>
          <Menu.Menu position="right" className="HorizontalPanel_closeButton__container">
            {headerMenuItems}
            <Menu.Item className="horizontal-panel_close-button">
              <Button icon="close" onClick={() => onClose()} />
            </Menu.Item>
          </Menu.Menu>
        </Menu>
      </Grid.Column>
    </Grid>
    <div className="horizontal-panel__content__container">
      <div className="horizontal-panel__content">{content}</div>
    </div>
  </div>
);

HorizontalPanel.propTypes = {
  content: PropTypes.object.isRequired,
  title: PropTypes.object.isRequired,
  headerMenuItems: PropTypes.array,
  onClose: PropTypes.func.isRequired,
  titleColumns: PropTypes.number,
  menuColumns: PropTypes.number,
  headerSize: PropTypes.string,
};

export default enhance(HorizontalPanel);
