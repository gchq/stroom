/*
 * Copyright 2026 Crown Copyright
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

package stroom.data.client.component;

import stroom.document.client.event.OpenDocumentEvent;
import stroom.feed.client.event.CopyFeedUrlEvent;
import stroom.feed.client.event.OpenFeedEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.shared.AppPermission;
import stroom.util.client.cell.DocRefCell;
import stroom.util.client.cell.FeedRefCell;
import stroom.util.client.cell.UserRefCell;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Wires a list's reference cells ([DocRefCell], [FeedRefCell] and [UserRefCell]) to the app. The
/// cells only ask: opening a document, feed or user, and copying a feed's link, go out from here as
/// the app-wide events that do them, and a user's open button is shown only to those who may open
/// that user.
///
/// A list injects one, owns it and configures each cell's builder with it:
///
/// ```java
/// this.refCells = own(refCells);
/// ...
/// DataGridUtil.docRefColumnBuilder(refCells.configure(new DocRefCell.Builder<Row>()...));
/// ```
public class RefCellsController extends Controller {

    private final ClientSecurityContext securityContext;

    /// Made by GIN, for the list that owns it.
    ///
    /// @param eventBus        The application's event bus, which the open and copy events go on.
    /// @param securityContext Who the current user is and what they may do.
    @Inject
    public RefCellsController(final EventBus eventBus,
                              final ClientSecurityContext securityContext) {
        super(eventBus);
        this.securityContext = securityContext;
    }

    /// Lets a document cell open its documents.
    ///
    /// @param builder The cell's builder.
    /// @param <T>     The row's type.
    /// @return The builder, for chaining.
    public <T> DocRefCell.Builder<T> configure(final DocRefCell.Builder<T> builder) {
        return builder.onOpen(docRef -> OpenDocumentEvent.fire(this, docRef, true));
    }

    /// Lets a feed cell open its feeds and copy their links.
    ///
    /// @param builder The cell's builder.
    /// @param <T>     The row's type.
    /// @return The builder, for chaining.
    public <T> FeedRefCell.Builder<T> configure(final FeedRefCell.Builder<T> builder) {
        return builder
                .onOpen(name -> OpenFeedEvent.fire(this, name, true))
                .onCopyUrl(name -> CopyFeedUrlEvent.fire(this, name));
    }

    /// Lets a user cell open the users the current user may open (see [#canOpen]).
    ///
    /// @param builder The cell's builder.
    /// @param <T>     The row's type.
    /// @return The builder, for chaining.
    public <T> UserRefCell.Builder<T> configure(final UserRefCell.Builder<T> builder) {
        return builder
                .canOpen(userRef -> canOpen(securityContext, userRef))
                .onOpen(userRef -> OpenUserEvent.fire(this, userRef));
    }

    /// Whether the current user may open a user: any, if they manage users, else only themself.
    ///
    /// @param securityContext Who the current user is and what they may do.
    /// @param userRef         The user to open.
    /// @return Whether they may open it.
    static boolean canOpen(final ClientSecurityContext securityContext, final UserRef userRef) {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
               || securityContext.isCurrentUser(userRef);
    }
}
