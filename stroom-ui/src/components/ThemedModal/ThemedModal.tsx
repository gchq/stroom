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

import * as React from "react";
import * as ReactModal from "react-modal";

import reactModalOptions from "./reactModalOptions";
import { useTheme } from "../../lib/theme";

export interface Props extends ReactModal.Props {
  header: JSX.Element;
  content: JSX.Element;
  actions: JSX.Element;
}

/**
 * A themed modal is required because Semantic UI modals are mounted
 * outside the application's root div. This means it won't inherit the
 * 'theme-dark' or 'theme-light' class name. We can add it here easily
 * enough.
 * property, or not.
 */
const ThemedModal = ({ header, content, actions, ...rest }: Props) => {
  const { theme } = useTheme();

  return (
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
};

export default ThemedModal;
