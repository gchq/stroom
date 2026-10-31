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

package stroom.mcp.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiBuildsConfig;
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/// The write guard: everything the agent makes lives in its workspace and carries its tags.
///
/// Creates land in `System/<workspace>/<build>/` (the workspace folder is `stroom.ai.builds.workspaceFolder`);
/// updates are allowed only on documents tagged [#MANAGED]. Changing anything else (a production XSLT, say) goes
/// through a working copy that `promote_build` writes back after approval and a backup. A document's build is the
/// folder it is in; what else is remembered about it is kept in the build's record ([BuildRecords]), not in tags.
///
/// Every read and write is made through Stroom's explorer services as the current user, so Stroom's own
/// permission checks apply (Create in the folder, Edit to tag, Delete to remove); the guard's checks come on top,
/// and its refusals are audited.
///
/// A tool that writes calls [#create] (or [#createFilled]) to make a document, and [#checkManaged] or
/// [#checkInBuild] before changing one.
@Singleton
public class WriteGuard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WriteGuard.class);

    /// The tag on what the agent may change; it comes off when a build is promoted.
    public static final String MANAGED = "mcp-managed";
    /// The tag on everything the tools create, for good: find it in Stroom by this tag, promoted or not.
    public static final String GENERATED = "mcp-generated";
    /// The tag on an XSLT saved with the mapping (or plan) it is generated from, kept in its description: outside
    /// the description, so a mapping deleted from its Documentation tab is known to have been there.
    public static final String KEPT_MAPPING = "mcp-kept-mapping";
    /// A build's documents by role, each in a subfolder of the build (asked for by the user): the build is still
    /// the top folder under the workspace, so a document in any of them is in the build. Feeds, the build's record
    /// and its notes stay at the build's top. A translation's folder is named for what it writes (asked for by the
    /// user): Events, or Records for a pipeline writing Records streams (live: STROOM_PROXY-RECEIVE-EVENTS, Raw
    /// Events made Records by 'Stroom Proxy Receive Events', its feed's own stream type Raw Events all the same).
    public static final List<String> ROLES = List.of("Events", "Records", "Indexing", "CEF", "Reference");
    /// The roles of a pipeline that reads a source's raw data.
    public static final List<String> SOURCE_ROLES = List.of("Events", "Records");
    /// The role of a pipeline of each stage.
    public static final Map<String, String> STAGE_ROLES = PipelineShapes.STAGE_ROLES;
    /// How many locks folders are made under.
    static final int FOLDER_LOCKS = 64;

    private static final Pattern BUILD = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{1,63}$");
    private static final Set<String> TAGS = Set.of(MANAGED, GENERATED);

    private final Provider<ExplorerService> explorerServiceProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final DocumentAccess documentAccess;
    private final Provider<AiConfig> aiConfigProvider;
    private final Provider<AiAuditLog> auditLogProvider;
    private final Provider<BuildRecords> buildRecordsProvider;
    // One folder made at a time per parent and name: two calls at once (an agent's parallel tool calls) each found
    // no folder and each made one. A fixed set of locks, each parent and name always the same one (a lock per
    // name, never removed, grew with every folder ever made).
    private final Object[] folderLocks = folderLocks();

    /// @param explorerServiceProvider     Creates, moves, tags and deletes documents, as the user.
    /// @param explorerNodeServiceProvider Reads the explorer tree.
    /// @param documentAccess              Checks View, and reads documents' nodes and folders.
    /// @param aiConfigProvider            Gives the workspace folder.
    /// @param auditLogProvider            Records the guard's refusals.
    /// @param buildRecordsProvider        Keeps what a working copy was made from.
    @Inject
    public WriteGuard(final Provider<ExplorerService> explorerServiceProvider,
                      final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                      final DocumentAccess documentAccess,
                      final Provider<AiConfig> aiConfigProvider,
                      final Provider<AiAuditLog> auditLogProvider,
                      final Provider<BuildRecords> buildRecordsProvider) {
        this.explorerServiceProvider = explorerServiceProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.documentAccess = documentAccess;
        this.aiConfigProvider = aiConfigProvider;
        this.auditLogProvider = auditLogProvider;
        this.buildRecordsProvider = buildRecordsProvider;
    }

    /// @return The name of the workspace folder, below System, that holds a folder for each build.
    public String workspaceFolder() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getBuilds, AiBuildsConfig::getWorkspaceFolder,
                AiBuildsConfig.DEFAULT_WORKSPACE_FOLDER);
    }

    /// @return The System root, as a folder.
    public Folder systemFolder() {
        return new Folder(ExplorerConstants.SYSTEM_DOC_REF, ExplorerConstants.SYSTEM, false);
    }

    /// @param parent A folder.
    /// @param name   A folder's name.
    /// @return The parent's folder of that name that the user can view, or empty.
    public Optional<Folder> findChildFolder(final Folder parent, final String name) {
        try {
            final ExplorerNode parentNode = node(parent.docRef());
            return NullSafe.list(explorerNodeServiceProvider.get().getNodesByNameAndType(parentNode, name,
                            ExplorerConstants.FOLDER_TYPE)).stream()
                    .filter(node -> node.getDocRef() != null)
                    .filter(node -> name.equals(node.getName()))
                    .filter(node -> documentAccess.canView(node.getDocRef()))
                    .findFirst()
                    .map(node -> new Folder(node.getDocRef(), parent.path() + "/" + name, false));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to look for folder {} in {}", name, parent.path()), e);
            return Optional.empty();
        }
    }

    /// The parent's folder of that name: the one already there if there is one, else a new one tagged `tags`
    /// ([Folder#created()]). Stroom lets a parent hold several folders of the same name, so making one without
    /// looking first left twins (asked for by the user). One already there keeps its tags.
    ///
    /// @param parent A folder.
    /// @param name   The folder's name.
    /// @param tags   The tags a new folder is given.
    /// @return The folder.
    public Folder createFolder(final Folder parent, final String name, final Set<String> tags) {
        final Object lock = folderLock(parent.docRef().getUuid(), name);
        synchronized (lock) {
            final Optional<Folder> found = findChildFolder(parent, name);
            if (found.isPresent()) {
                return found.get();
            }
            final ExplorerNode made = explorerServiceProvider.get().create(ExplorerConstants.FOLDER_TYPE, name,
                    node(parent.docRef()), PermissionInheritance.DESTINATION);
            tag(List.of(made.getDocRef()), tags);
            return new Folder(made.getDocRef(), parent.path() + "/" + name, true);
        }
    }

    /// @param parentUuid A folder's parent.
    /// @param name       The folder's name.
    /// @return The lock a folder of that name is made under in that parent: always the same one, of a fixed set.
    Object folderLock(final String parentUuid, final String name) {
        return folderLocks[Math.floorMod((parentUuid + "/" + name).hashCode(), folderLocks.length)];
    }

    private static Object[] folderLocks() {
        final Object[] locks = new Object[FOLDER_LOCKS];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    /// The deepest folder that exists along an explorer path such as `System/Feeds/Events/Acme`, and the names
    /// below it that don't exist yet (empty when the whole path does). Creates nothing.
    ///
    /// @param path An explorer path, as [#folderParts(String)] takes it.
    /// @return The folder, and the names missing below it.
    /// @throws ToolRefusal if the path names no folder.
    public ResolvedFolder resolveFolder(final String path) {
        final List<String> parts = folderParts(path);
        Folder folder = systemFolder();
        for (int i = 1; i < parts.size(); i++) {
            final Optional<Folder> child = findChildFolder(folder, parts.get(i));
            if (child.isEmpty()) {
                return new ResolvedFolder(folder, List.copyOf(parts.subList(i, parts.size())));
            }
            folder = child.get();
        }
        return new ResolvedFolder(folder, List.of());
    }

    /// @param build A build's name.
    /// @return The build's folder, the workspace and build folders made if needed.
    /// @throws ToolRefusal if the name isn't a build name.
    public Folder buildFolder(final String build) {
        return buildFolder(build, true).orElseThrow();
    }

    /// @param build  A build's name.
    /// @param create True to make the workspace and build folders if needed; false for reads, which mustn't bring
    ///               back a folder that promotion removed.
    /// @return The build's folder, or empty when it has none and `create` is false.
    /// @throws ToolRefusal if the name isn't a build name.
    public Optional<Folder> buildFolder(final String build, final boolean create) {
        if (build == null || !BUILD.matcher(build).matches()) {
            throw new ToolRefusal("Build names are 2 to 64 letters, digits, '.', '_' or '-', e.g. 'keycloak-v1.3'");
        }
        final Folder system = systemFolder();
        if (create) {
            return Optional.of(managedFolder(managedFolder(system, workspaceFolder()), build));
        }
        return findChildFolder(system, workspaceFolder())
                .flatMap(workspace -> findChildFolder(workspace, build));
    }

    /// @param build A build's name.
    /// @param role  One of [#ROLES].
    /// @return The build's subfolder for the role, made if needed.
    /// @throws ToolRefusal if the role is not one of them.
    public Folder roleFolder(final String build, final String role) {
        if (!ROLES.contains(role)) {
            throw new ToolRefusal("Unknown role '" + role + "': one of " + String.join(", ", ROLES));
        }
        return managedFolder(buildFolder(build), role);
    }

    /// Moves one of the build's documents into another role's subfolder: a text converter or translation XSLT,
    /// made before its pipeline, goes where the pipeline writing Events or Records went.
    ///
    /// @param build The build.
    /// @param ref   One of its documents.
    /// @param role  The role to move it to.
    /// @return True if it moved.
    public boolean moveToRole(final String build, final DocRef ref, final String role) {
        final BuildDoc here = folderContents(build).stream()
                .filter(doc -> doc.uuid().equals(ref.getUuid()))
                .findFirst()
                .orElse(null);
        if (here == null || Objects.equals(here.role(), role)) {
            return false;
        }
        final Folder folder = roleFolder(build, role);
        explorerServiceProvider.get().move(List.of(node(here.docRef())), node(folder.docRef()),
                PermissionInheritance.DESTINATION);
        return true;
    }

    /// @param build A build's name.
    /// @return The documents the user can view in the build folder and its role subfolders, without the build's
    /// own record; none when it has no folder.
    public List<BuildDoc> folderContents(final String build) {
        return folderContents(build, false);
    }

    /// @param build      A build's name.
    /// @param withRecord True to include the build's own record ([BuildRecords#NAME]).
    /// @return The documents the user can view in the build folder and its role subfolders; none when it has no
    /// folder.
    public List<BuildDoc> folderContents(final String build, final boolean withRecord) {
        final Optional<Folder> folder = buildFolder(build, false);
        if (folder.isEmpty()) {
            return List.of();
        }
        final Map<String, BuildDoc> docs = new LinkedHashMap<>();
        final List<ExplorerNode> children = documentAccess.children(folder.get().docRef());
        for (final ExplorerNode child : children) {
            if (!ExplorerConstants.isFolder(child)) {
                docs.put(child.getUuid(), buildDoc(child, folder.get().path(), null));
            }
        }
        for (final ExplorerNode sub : children) {
            if (ExplorerConstants.isFolder(sub) && ROLES.contains(sub.getName())) {
                final String path = folder.get().path() + "/" + sub.getName();
                for (final ExplorerNode child : documentAccess.children(sub.getDocRef())) {
                    if (!ExplorerConstants.isFolder(child)) {
                        docs.putIfAbsent(child.getUuid(), buildDoc(child, path, sub.getName()));
                    }
                }
            }
        }
        return docs.values().stream()
                .filter(doc -> withRecord || !isRecord(doc))
                .toList();
    }

    /// @param ref A document.
    /// @return The build it is in: the folder it sits in under the workspace. Empty outside the workspace, or for
    /// a document the user can't view or that doesn't exist.
    public Optional<String> buildOf(final DocRef ref) {
        if (ref == null || !documentAccess.canView(ref) || documentAccess.node(ref).isEmpty()) {
            return Optional.empty();
        }
        // /<workspace>/<build>/... : the folder holding the document, below System.
        final List<String> parts = Arrays.stream(NullSafe.string(documentAccess.path(ref)).split("/"))
                .filter(part -> !part.isEmpty())
                .toList();
        return parts.size() >= 2 && parts.getFirst().equals(workspaceFolder())
                ? Optional.of(parts.get(1))
                : Optional.empty();
    }

    /// @param ref A document in a build.
    /// @return The production document a working copy in a build was made from; empty for a new document.
    public Optional<String> copyOf(final DocRef ref) {
        final Optional<String> build = buildOf(ref);
        return build.flatMap(name -> Optional.ofNullable(
                NullSafe.get(buildRecordsProvider.get().entry(name, ref.getUuid()).get("copy_of"), String::valueOf)));
    }

    /// Deletes a build's folder once nothing at all is left in it, subfolders included (empty role subfolders go
    /// with it). Stroom deletes a folder with everything in it, so every child is counted, whoever may view it.
    ///
    /// @param build A build's name.
    /// @return True if the folder was deleted.
    public boolean removeBuildFolderIfEmpty(final String build) {
        final Optional<Folder> folder = buildFolder(build, false);
        if (folder.isEmpty() || !folderContents(build).isEmpty()) {
            return false;
        }
        final ExplorerNodeService nodes = explorerNodeServiceProvider.get();
        final List<ExplorerNode> children = NullSafe.list(nodes.getChildren(folder.get().docRef()));
        final boolean onlyEmptyRoles = children.stream().allMatch(child -> ExplorerConstants.isFolder(child)
                                                                            && ROLES.contains(child.getName())
                                                                            && NullSafe.isEmptyCollection(
                nodes.getChildren(child.getDocRef())));
        if (!onlyEmptyRoles) {
            return false;
        }
        explorerServiceProvider.get().delete(List.of(node(folder.get().docRef())));
        return nodes.getNode(folder.get().docRef()).isEmpty();
    }

    /// Creates an empty document in the build folder and tags it as the agent's. A name the build already has for
    /// that type is refused, naming the one there: after a long conversation was summarised, an agent made the same
    /// indexing pipeline twice, and the build held two.
    ///
    /// @param type   The document's type, e.g. `XSLT`.
    /// @param name   Its name.
    /// @param build  The build.
    /// @param copyOf The production document it is a working copy of, kept in the build's record; null for a new
    ///               document.
    /// @param record True only for the build's own record ([BuildRecords#NAME]).
    /// @param role   The role subfolder to make it in, or null for the build's top.
    /// @return The document.
    /// @throws ToolRefusal if the name is the record's, or the build has a document of that type and name.
    public DocRef create(final String type,
                         final String name,
                         final String build,
                         final String copyOf,
                         final boolean record,
                         final String role) {
        if (DocumentationDoc.TYPE.equals(type) && BuildRecords.NAME.equals(name) && !record) {
            throw new ToolRefusal("'" + name + "' is the server's own record of the build: give the Documentation "
                                  + "another name");
        }
        final Optional<BuildDoc> twin = folderContents(build, true).stream()
                .filter(doc -> doc.type().equals(type) && doc.name().equals(name))
                .findFirst();
        if (twin.isPresent()) {
            throw new ToolRefusal("A " + type + " named '" + name + "' is already in build " + build + " (uuid "
                                  + twin.get().uuid() + "): carry on with that one (build_status lists the build's "
                                  + "documents), or give the new one another name.");
        }
        final Folder folder = role == null
                ? buildFolder(build)
                : roleFolder(build, role);
        final DocRef made = explorerServiceProvider.get().create(type, name, node(folder.docRef()),
                PermissionInheritance.DESTINATION).getDocRef();
        tag(List.of(made), TAGS);
        if (copyOf != null) {
            buildRecordsProvider.get().update(build, made, entry -> entry.put("copy_of", copyOf));
        }
        return made;
    }

    /// Creates a document and fills it. Stroom creates documents empty, so if filling fails the document is deleted
    /// again rather than left behind empty, and the error is thrown.
    ///
    /// @param type   The document's type.
    /// @param name   Its name.
    /// @param build  The build.
    /// @param fill   Fills the new document, e.g. by writing its content, and gives what the caller wants back.
    /// @param record True only for the build's own record.
    /// @param role   The role subfolder to make it in, or null for the build's top.
    /// @param <T>    What filling gives.
    /// @return What filling gave.
    public <T> T createFilled(final String type,
                              final String name,
                              final String build,
                              final Function<DocRef, T> fill,
                              final boolean record,
                              final String role) {
        final DocRef ref = create(type, name, build, null, record, role);
        try {
            return fill.apply(ref);
        } catch (final RuntimeException e) {
            try {
                explorerServiceProvider.get().delete(List.of(node(ref)));
            } catch (final RuntimeException deleteFailed) {
                LOGGER.warn(() -> LogUtil.message(
                        "Couldn't delete the empty {} '{}' ({}) after filling it failed",
                        type,
                        name,
                        ref.getUuid()),
                        deleteFailed);
            }
            throw e;
        }
    }

    /// Moves a document into a folder, as the user (Stroom checks they may), its permissions the folder's: how
    /// promotion puts a build's document in place. Its UUID stays, so filters and references keep working.
    ///
    /// @param ref    The document.
    /// @param folder The folder.
    public void moveTo(final DocRef ref, final Folder folder) {
        explorerServiceProvider.get().move(List.of(node(ref)), node(folder.docRef()),
                PermissionInheritance.DESTINATION);
    }

    /// Copies a document into a folder under another name, as the user: promotion's backup of a production
    /// document before its working copy is written over it.
    ///
    /// @param ref    The document.
    /// @param folder The folder.
    /// @param name   The copy's name.
    /// @return The copies made.
    public List<DocRef> copyTo(final DocRef ref, final Folder folder, final String name) {
        final BulkActionResult copied = explorerServiceProvider.get().copy(List.of(node(ref)), node(folder.docRef()),
                true, name, PermissionInheritance.DESTINATION);
        return NullSafe.list(NullSafe.get(copied, BulkActionResult::getExplorerNodes)).stream()
                .map(ExplorerNode::getDocRef)
                .filter(Objects::nonNull)
                .toList();
    }

    /// Deletes a document, as the user (Stroom checks they may).
    ///
    /// @param ref The document.
    public void delete(final DocRef ref) {
        explorerServiceProvider.get().delete(List.of(node(ref)));
    }

    /// @param ref A document.
    /// @return Its tags, or none if the user can't view it or it doesn't exist.
    public Set<String> tags(final DocRef ref) {
        if (ref == null || !documentAccess.canView(ref)) {
            return Set.of();
        }
        return documentAccess.node(ref)
                .map(ExplorerNode::getTags)
                .map(tags -> (Set<String>) new LinkedHashSet<>(tags))
                .orElse(Set.of());
    }

    /// @param session The session the call is made in, for the audit log.
    /// @param ref     A document a tool is about to change.
    /// @return Its tags.
    /// @throws ToolRefusal (audited) if it is not tagged [#MANAGED]: the tools didn't create it.
    public Set<String> checkManaged(final ToolSession session, final DocRef ref) {
        final Set<String> tags = tags(ref);
        if (!tags.contains(MANAGED)) {
            final String message = describe(ref) + " was not created by this server. Make a working copy in a build "
                                   + "(copy_pipeline) and change that instead; promote_build writes it back after "
                                   + "approval.";
            denied(session, ref, "not_managed", message);
            throw new ToolRefusal(message);
        }
        return tags;
    }

    /// A doc the tools created that sits in one of the workspace's build folders. Index docs and pipelines are
    /// changed only there (asked for by the user), whatever their tags say: a doc moved out of its build in Stroom
    /// keeps them.
    ///
    /// @param session The session the call is made in, for the audit log.
    /// @param ref     A document a tool is about to change.
    /// @return Its build.
    /// @throws ToolRefusal (audited) if it is not managed, or not in a build folder.
    public String checkInBuild(final ToolSession session, final DocRef ref) {
        checkManaged(session, ref);
        final Optional<String> build = buildOf(ref);
        if (build.isEmpty()) {
            final String message = describe(ref) + " is not in a build folder: it is changed only in a build. Make "
                                   + "a working copy in one (copy_pipeline), or move it back into its build folder "
                                   + "in Stroom.";
            denied(session, ref, "not_in_build", message);
            throw new ToolRefusal(message);
        }
        return build.get();
    }

    /// @param session The session the call is made in, for the audit log.
    /// @param ref     A document.
    /// @return Its tags.
    /// @throws ToolRefusal (audited) if the tools didn't generate it, whether it is still in the workspace or was
    ///                     promoted.
    public Set<String> checkBuilt(final ToolSession session, final DocRef ref) {
        final Set<String> tags = tags(ref);
        if (!tags.contains(GENERATED) && !tags.contains(MANAGED)) {
            final String message = describe(ref) + " was not built by this server";
            denied(session, ref, "not_built", message);
            throw new ToolRefusal(message);
        }
        return tags;
    }

    /// Adds tags to documents (the user needs Edit on each).
    ///
    /// @param refs The documents.
    /// @param tags The tags.
    public void tag(final List<DocRef> refs, final Set<String> tags) {
        explorerServiceProvider.get().addTags(refs, tags);
    }

    /// Removes tags from documents (the user needs Edit on each).
    ///
    /// @param refs The documents.
    /// @param tags The tags.
    public void untag(final List<DocRef> refs, final Set<String> tags) {
        explorerServiceProvider.get().removeTags(refs, tags);
    }

    /// An explorer path's folder names, starting with System: `System / Feeds` (as Stroom's explorer search gives
    /// paths), `System/Feeds/`, `/Feeds` (Stroom's own folder tools) and `Feeds` all work. The explorer has the one
    /// root, System: seen, `Feeds/Test/...` and `/Feeds/Test/...` refused for want of it, and the agent tried again.
    ///
    /// @param path An explorer path.
    /// @return Its folder names, the first System.
    /// @throws ToolRefusal if it names no folder.
    public static List<String> folderParts(final String path) {
        final List<String> parts = Arrays.stream(NullSafe.string(path).split("/"))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .toList();
        if (parts.isEmpty()) {
            throw new ToolRefusal("Destination '" + NullSafe.string(path) + "' is no folder: give an explorer path, "
                                  + "e.g. /Feeds/Events/Acme");
        }
        if (parts.getFirst().equals(ExplorerConstants.SYSTEM)) {
            return parts;
        }
        final List<String> withSystem = new ArrayList<>();
        withSystem.add(ExplorerConstants.SYSTEM);
        withSystem.addAll(parts);
        return List.copyOf(withSystem);
    }

    /// A folder path as Stroom's own explorer tools take it (`System / Feeds / Events` as `/Feeds/Events`, System as
    /// `/`): seen, a folder tool refused the explorer search's `System / Feeds / Events` and took `/Feeds/Events`.
    ///
    /// @param path A folder path, in any of the forms [#folderParts(String)] takes, or null.
    /// @return It from `/`, without System.
    public static String explorerPath(final String path) {
        final List<String> parts = Arrays.stream(NullSafe.string(path).split("/"))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .toList();
        final List<String> below = !parts.isEmpty() && parts.getFirst().equals(ExplorerConstants.SYSTEM)
                ? parts.subList(1, parts.size())
                : parts;
        return "/" + String.join("/", below);
    }

    // A folder of the tools' own, tagged as theirs if it is made.
    private Folder managedFolder(final Folder parent, final String name) {
        return createFolder(parent, name, TAGS);
    }

    private ExplorerNode node(final DocRef docRef) {
        if (ExplorerConstants.SYSTEM_DOC_REF.getUuid().equals(docRef.getUuid())) {
            return Objects.requireNonNullElse(explorerNodeServiceProvider.get().getRoot(),
                    ExplorerConstants.SYSTEM_NODE);
        }
        return explorerNodeServiceProvider.get().getNode(docRef)
                .orElseThrow(() -> new ToolRefusal(describe(docRef) + " is not in the explorer tree (deleted?)"));
    }

    private static BuildDoc buildDoc(final ExplorerNode node, final String path, final String role) {
        return new BuildDoc(node.getType(), node.getUuid(), node.getName(),
                List.copyOf(new LinkedHashSet<>(NullSafe.set(node.getTags()))), path, role);
    }

    private static boolean isRecord(final BuildDoc doc) {
        return DocumentationDoc.TYPE.equals(doc.type()) && BuildRecords.NAME.equals(doc.name());
    }

    private static String describe(final DocRef ref) {
        return ref.getType() + " '" + NullSafe.nonBlankStringElse(ref.getName(), ref.getUuid()) + "'";
    }

    private void denied(final ToolSession session, final DocRef ref, final String reason, final String message) {
        if (session != null) {
            auditLogProvider.get().processed(session, ref, "access_denied", reason, message, false);
        }
    }


    // --------------------------------------------------------------------------------


    /// A folder, by its explorer path.
    ///
    /// @param docRef  The folder.
    /// @param path    Its path from System, e.g. `System/MCP Workspace/acme-v1`.
    /// @param created True if it was made just now.
    public record Folder(DocRef docRef, String path, boolean created) {

        /// @return The folder's UUID.
        public String uuid() {
            return docRef.getUuid();
        }
    }

    /// The deepest folder along a path that exists, and the names below it that don't.
    ///
    /// @param folder  The folder.
    /// @param missing The names below it still to make, in order; empty when the whole path exists.
    public record ResolvedFolder(Folder folder, List<String> missing) {

    }

    /// One of a build's documents.
    ///
    /// @param type The document's type.
    /// @param uuid Its UUID.
    /// @param name Its name.
    /// @param tags Its tags.
    /// @param path The folder it is in, from System.
    /// @param role The role subfolder it is in, or null at the build's top.
    public record BuildDoc(String type, String uuid, String name, List<String> tags, String path, String role) {

        /// @return The document.
        public DocRef docRef() {
            return new DocRef(type, uuid, name);
        }

        /// @return It as a result lists it: `type`, `uuid`, `name`, `tags`, `path` and, in a role subfolder, `role`.
        public Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type);
            map.put("uuid", uuid);
            map.put("name", name);
            map.put("tags", tags);
            map.put("path", path);
            if (role != null) {
                map.put("role", role);
            }
            return map;
        }
    }
}
