/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.docstore.api;

import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

/**
 * Convenience binder for document stores that replaces the repetitive 5-statement
 * registration pattern with a single fluent call.
 * <p>
 * Instead of:
 * <pre>
 * bind(XxxStore.class).to(XxxStoreImpl.class);
 *
 * GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
 *         .addBinding(XxxStoreImpl.class);
 * GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
 *         .addBinding(XxxStoreImpl.class);
 * GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
 *         .addBinding(XxxStoreImpl.class);
 *
 * DocumentActionHandlerBinder.create(binder())
 *         .bind(XxxDoc.TYPE, XxxStoreImpl.class);
 * </pre>
 * <p>
 * You can now write:
 * <pre>
 * DocumentStoreBinder.create(binder())
 *         .bind(XxxDoc.TYPE, XxxStore.class, XxxStoreImpl.class);
 * </pre>
 */
public class DocumentStoreBinder {

    private final Binder binder;

    private DocumentStoreBinder(final Binder binder) {
        this.binder = binder;
    }

    public static DocumentStoreBinder create(final Binder binder) {
        return new DocumentStoreBinder(binder);
    }

    /**
     * Registers a document store implementation with all standard Guice bindings:
     * <ol>
     *     <li>Binds the store interface to the implementation class</li>
     *     <li>Adds the implementation to the {@link ExplorerActionHandler} multibinder</li>
     *     <li>Adds the implementation to the {@link ImportExportActionHandler} multibinder</li>
     *     <li>Adds the implementation to the {@link ContentIndexable} multibinder</li>
     *     <li>Adds the implementation to the {@link DocumentActionHandler} map binder</li>
     * </ol>
     *
     * @param type           The document type string (e.g. {@code XxxDoc.TYPE})
     * @param storeInterface The store interface class (e.g. {@code XxxStore.class})
     * @param storeImpl      The store implementation class (e.g. {@code XxxStoreImpl.class})
     * @param <S>            The store interface type
     * @return This binder for fluent chaining
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <S> DocumentStoreBinder bind(final String type,
                                        final Class<S> storeInterface,
                                        final Class<? extends S> storeImpl) {
        binder.bind(storeInterface).to(storeImpl);
        bindHandlers(type, (Class) storeImpl);
        return this;
    }

    /**
     * Registers only the handler bindings (multibinders + DocumentActionHandler map) without
     * binding a store interface. Use this when the store interface binding is done separately
     * or when only handler registration is needed.
     *
     * @param type      The document type string (e.g. {@code XxxDoc.TYPE})
     * @param storeImpl The store implementation class (must implement DocumentStore)
     * @return This binder for fluent chaining
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DocumentStoreBinder bindHandlers(final String type,
                                            final Class<? extends DocumentStore> storeImpl) {
        Multibinder.newSetBinder(binder, ExplorerActionHandler.class)
                .addBinding().to(storeImpl);
        Multibinder.newSetBinder(binder, ImportExportActionHandler.class)
                .addBinding().to(storeImpl);
        Multibinder.newSetBinder(binder, ContentIndexable.class)
                .addBinding().to(storeImpl);

        DocumentActionHandlerBinder.create(binder)
                .bind(type, (Class) storeImpl);

        return this;
    }

}
