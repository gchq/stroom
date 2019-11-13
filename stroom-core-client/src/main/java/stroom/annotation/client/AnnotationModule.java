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

package stroom.annotation.client;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;
import stroom.annotation.client.AnnotationEditPresenter.AnnotationEditView;
import stroom.annotation.client.ChooserPresenter.ChooserView;

public class AnnotationModule extends AbstractPresenterModule {
    @Override
    protected void configure() {
        bind(AnnotationEditSupport.class).asEagerSingleton();
        bindPresenterWidget(AnnotationEditPresenter.class, AnnotationEditView.class, AnnotationEditViewImpl.class);
        bindPresenterWidget(ChooserPresenter.class, ChooserView.class, ChooserViewImpl.class);
    }
}
