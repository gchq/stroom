/*
 * Copyright 2016 Crown Copyright
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

package stroom.ai.client.gin;

import stroom.ai.client.AskStroomAiConfigPresenter;
import stroom.ai.client.AskStroomAiConfigPresenter.AskStroomAiConfigView;
import stroom.ai.client.AskStroomAiConfigViewImpl;
import stroom.ai.client.AskStroomAiPresenter;
import stroom.ai.client.AskStroomAiPresenter.AskStroomAiView;
import stroom.ai.client.AskStroomAiViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

public class AskStroomAIModule extends AbstractPresenterModule {

    @Override
    protected void configure() {
        bindPresenter(
                AskStroomAiPresenter.class,
                AskStroomAiView.class,
                AskStroomAiViewImpl.class,
                AskStroomAiPresenter.AskStroomAiProxy.class);
        bindPresenterWidget(
                AskStroomAiConfigPresenter.class,
                AskStroomAiConfigView.class,
                AskStroomAiConfigViewImpl.class);
    }
}
