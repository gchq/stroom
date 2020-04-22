/*
 * Copyright 2020 Crown Copyright
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
import { useEffect } from "react";
import "react-toggle/style.css";
import useAppNavigation from "lib/useAppNavigation";
import useIdFromPath from "lib/useIdFromPath";
import useTokens from "./useTokens";
import EditTokenForm from "./EditTokenForm";
import CustomLoader from "../../CustomLoader";

const EditTokenContainer = () => {
  const { toggleEnabledState, fetchApiKey, token } = useTokens();
  const tokenId = useIdFromPath("apikey/");
  const {
    nav: { goToApiKeys },
  } = useAppNavigation();

  useEffect(() => {
    if (!!tokenId) {
      fetchApiKey(tokenId);
    }
  }, [tokenId, fetchApiKey]);

  if (!token) {
    return <CustomLoader title="Stroom" message="Loading. Please wait..."/>;
  }

  return (
    <EditTokenForm
      onBack={goToApiKeys}
      onChangeState={toggleEnabledState}
      token={token}
    />
  );
};

export default EditTokenContainer;
