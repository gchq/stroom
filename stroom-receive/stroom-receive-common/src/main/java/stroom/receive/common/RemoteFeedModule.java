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

package stroom.receive.common;

import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

// TODO maybe rename to ReceiveModule
public class RemoteFeedModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(RequestAuthenticator.class).to(RequestAuthenticatorImpl.class);
        bind(DataFeedKeyService.class).to(DataFeedKeyServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(FeedStatusResourceImpl.class);

//        ServletBinder.create(binder())
//                .bind(RemoteFeedServiceRPC.class);
    }
}
