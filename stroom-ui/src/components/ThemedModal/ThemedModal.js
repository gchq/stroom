/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Button } from 'semantic-ui-react';
import ReactModal from 'react-modal';

import IconHeader from 'components/IconHeader';

const enhance = compose(
  connect(
    ({ userSettings: { theme } }) => ({
      theme,
    }),
    {},
  ),
  withProps(({ theme }) => ({
    dimmer: theme === 'theme-light' ? 'inverted' : true,
  })),
);

const reactModalOptions = {
  overlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(255, 255, 255, 0.75)'
  },
  content: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    // right: '40px',
    // bottom: '40px',
    border: '1px solid #ccc',
    background: '#fff',
    overflow: 'auto',
    WebkitOverflowScrolling: 'touch',
    borderRadius: '4px',
    outline: 'none',
    padding: '0'
  }
};
/**
 * A themed modal is required because Semantic UI modals are mounted
 * outside the application's root div. This means it won't inherit the
 * 'theme-dark' or 'theme-light' class name. We can add it here easily
 * enough, and it gives us the opportunity to set the SUI dimmer
 * property, or not.
 */
let ThemedModal = ({
  dimmer, theme, header, content, actions, ...rest
}) => (
    <ReactModal className={`${theme}`} {...rest} style={reactModalOptions}>
      <div className="raised-low themed-modal">
        <header className="raised-low themed-modal__header">{header}</header>
        <div className="raised-low themed-modal__content">{content}</div>
        <div className="raised-low themed-modal__footer__actions">
          {actions}
        </div>
      </div>
    </ReactModal>
  );

ThemedModal = enhance(ThemedModal);

let ThemedConfirm = ({
  dimmer, theme, question, details, onCancel, onConfirm, ...rest
}) => (
    <ReactModal className={`${theme}`} {...rest} style={reactModalOptions}>
      <div className="raised-low themed-modal">
        <header className="raised-low themed-modal__header">
          <IconHeader text={question} icon='question-circle' />
        </header>
        {details && <div className="raised-low themed-modal__content">{details}</div>}
        <div className="raised-low themed-modal__footer__actions">
          <Button negative onClick={onCancel}>
            Cancel
          </Button>
          <Button
            positive
            onClick={onConfirm}
            labelPosition="right"
            icon="checkmark"
            content="Confirm"
          >
          </Button>
        </div>
      </div>
    </ReactModal>
  );

ThemedConfirm = enhance(ThemedConfirm);

export default enhance(ThemedModal);

export { ThemedModal, ThemedConfirm };
