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
    componentWillUnmount() {
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
  headerSize,
}) => (
    <div className="horizontal-panel__container">
      <div className="horizontal-panel_header-container flat">
        <div className="HorizontalPanel_title__container">
          {title}
        </div>
        <div>

          <div>
            <Menu secondary>
              <Menu.Menu position="right" className="HorizontalPanel_closeButton__container">
                {headerMenuItems}
                <Menu.Item className="horizontal-panel_close-button ">
                  <Button className="icon-button" icon="close" onClick={() => onClose()} />
                </Menu.Item>
              </Menu.Menu>
            </Menu>
          </div>
        </div>
      </div>
      <div className="horizontal-panel__content__container">
        <div className="horizontal-panel__content">
          {content}
        </div>
      </div>
    </div>
  );

HorizontalPanel.propTypes = {
  content: PropTypes.object.isRequired,
  title: PropTypes.object.isRequired,
  headerMenuItems: PropTypes.array,
  onClose: PropTypes.func.isRequired,
  headerSize: PropTypes.string,
};

export default enhance(HorizontalPanel);
