package stroom.security.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.security.impl.DocTypeIdDao;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.UserDocumentPermissions;
import stroom.security.impl.db.jooq.tables.PermissionDoc;
import stroom.security.impl.db.jooq.tables.PermissionDocCreate;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.jooq.types.UByte;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.security.impl.db.jooq.tables.PermissionDoc.PERMISSION_DOC;
import static stroom.security.impl.db.jooq.tables.PermissionDocCreate.PERMISSION_DOC_CREATE;
import static stroom.security.impl.db.jooq.tables.StroomUser.STROOM_USER;

public class DocumentPermissionDaoImpl implements DocumentPermissionDao {

    private static final PermissionDoc PERMISSION_DOC_SOURCE = new PermissionDoc("pd_source");
    private static final PermissionDocCreate PERMISSION_DOC_CREATE_SOURCE = new PermissionDocCreate("pdc_source");

    private final SecurityDbConnProvider securityDbConnProvider;
    private final DocTypeIdDao docTypeIdDao;
    private final UserDaoImpl userDao;

    @Inject
    public DocumentPermissionDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                                     final DocTypeIdDao docTypeIdDao,
                                     final UserDaoImpl userDao) {
        this.securityDbConnProvider = securityDbConnProvider;
        this.docTypeIdDao = docTypeIdDao;
        this.userDao = userDao;
    }

    @Override
    public UserDocumentPermissions getPermissionsForUser(final String userUuid) {
        Objects.requireNonNull(userUuid, "Null user UUID");

        final Map<String, Byte> permissionMap = new ConcurrentHashMap<>();
        JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(PERMISSION_DOC.DOC_UUID, PERMISSION_DOC.PERMISSION_ID)
                        .from(PERMISSION_DOC)
                        .where(PERMISSION_DOC.USER_UUID.eq(userUuid))
                        .fetch())
                .forEach(r -> {
                    final String docUuid = r.get(PERMISSION_DOC.DOC_UUID);
                    final byte permission = r.get(PERMISSION_DOC.PERMISSION_ID).byteValue();
                    permissionMap.put(docUuid, permission);
                });
        return new UserDocumentPermissions(permissionMap);
    }

    @Override
    public DocumentPermission getDocumentUserPermission(final String documentUuid, final String userUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");

        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(PERMISSION_DOC.PERMISSION_ID)
                        .from(PERMISSION_DOC)
                        .where(PERMISSION_DOC.DOC_UUID.eq(documentUuid))
                        .and(PERMISSION_DOC.USER_UUID.eq(userUuid))
                        .fetchOptional(PERMISSION_DOC.PERMISSION_ID))
                .map(r -> DocumentPermission.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(r.byteValue()))
                .orElse(null);
    }

    @Override
    public void setDocumentUserPermission(final String documentUuid,
                                          final String userUuid,
                                          final DocumentPermission permission) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");
        Objects.requireNonNull(permission, "Null permission");

        final UByte permissionId = UByte.valueOf(permission.getPrimitiveValue());
        JooqUtil.context(securityDbConnProvider, context -> context
                .insertInto(PERMISSION_DOC)
                .columns(PERMISSION_DOC.DOC_UUID,
                        PERMISSION_DOC.USER_UUID,
                        PERMISSION_DOC.PERMISSION_ID)
                .values(documentUuid, userUuid, permissionId)
                .onDuplicateKeyUpdate()
                .set(PERMISSION_DOC.PERMISSION_ID, permissionId)
                .execute());
    }

    @Override
    public void removeDocumentUserPermission(final String documentUuid, final String userUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");

        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC)
                .where(PERMISSION_DOC.DOC_UUID.eq(documentUuid))
                .and(PERMISSION_DOC.USER_UUID.eq(userUuid))
                .execute());
    }

    @Override
    public void removeAllDocumentPermissions(final String documentUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");

        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC)
                .where(PERMISSION_DOC.DOC_UUID.eq(documentUuid))
                .execute());
    }

    @Override
    public void clearAllDocumentPermissions() {
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC)
                .execute());
    }

    @Override
    public List<Integer> getDocumentUserCreatePermissions(final String documentUuid, final String userUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");

        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                        .from(PERMISSION_DOC_CREATE)
                        .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(documentUuid))
                        .and(PERMISSION_DOC_CREATE.USER_UUID.eq(userUuid))
                        .fetch())
                .map(r -> r.get(PERMISSION_DOC_CREATE.DOC_TYPE_ID).intValue());
    }

    @Override
    public void addDocumentUserCreatePermission(final String documentUuid,
                                                final String userUuid,
                                                final String documentType) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");
        Objects.requireNonNull(documentType, "Null document type");

        final UByte docTypeId = UByte.valueOf(docTypeIdDao.getOrCreateId(documentType));
        JooqUtil.context(securityDbConnProvider, context -> context
                .insertInto(PERMISSION_DOC_CREATE)
                .columns(PERMISSION_DOC_CREATE.DOC_UUID,
                        PERMISSION_DOC_CREATE.USER_UUID,
                        PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                .values(documentUuid, userUuid, docTypeId)
                .onDuplicateKeyUpdate()
                .set(PERMISSION_DOC_CREATE.DOC_TYPE_ID, docTypeId)
                .execute());
    }

    @Override
    public void removeDocumentUserCreatePermission(final String documentUuid,
                                                   final String userUuid,
                                                   final String documentType) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");
        Objects.requireNonNull(documentType, "Null document type");

        final UByte docTypeId = UByte.valueOf(docTypeIdDao.getOrCreateId(documentType));
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC_CREATE)
                .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(documentUuid))
                .and(PERMISSION_DOC_CREATE.USER_UUID.eq(userUuid))
                .and(PERMISSION_DOC_CREATE.DOC_TYPE_ID.eq(docTypeId))
                .execute());
    }

    @Override
    public void removeDocumentUserCreatePermissions(final String documentUuid, final String userUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");
        Objects.requireNonNull(userUuid, "Null user UUID");

        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC_CREATE)
                .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(documentUuid))
                .and(PERMISSION_DOC_CREATE.USER_UUID.eq(userUuid))
                .execute());
    }

    @Override
    public void removeAllDocumentCreatePermissions(final String documentUuid) {
        Objects.requireNonNull(documentUuid, "Null document UUID");

        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC_CREATE)
                .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(documentUuid))
                .execute());
    }

    @Override
    public void clearAllDocumentCreatePermissions() {
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(PERMISSION_DOC_CREATE)
                .execute());
    }

    @Override
    public void addDocumentPermissions(final String sourceDocUuid, final String destDocUuid) {
        Objects.requireNonNull(sourceDocUuid, "Null source document UUID");
        Objects.requireNonNull(destDocUuid, "Null destination document UUID");

        // Add permissions.
        JooqUtil.context(securityDbConnProvider, context ->
                addDocumentPermissions(context, sourceDocUuid, destDocUuid));
    }

    @Override
    public void setDocumentPermissions(final String sourceDocUuid, final String destDocUuid) {
        Objects.requireNonNull(sourceDocUuid, "Null source document UUID");
        Objects.requireNonNull(destDocUuid, "Null destination document UUID");

        JooqUtil.transaction(securityDbConnProvider, context -> {
            // Clear existing permssions.
            clearDocumentPermissions(context, destDocUuid);
            // Add new permissions.
            addDocumentPermissions(context, sourceDocUuid, destDocUuid);
        });
    }

    private void clearDocumentPermissions(final DSLContext context,
                                          final String docUuid) {
        // Clear permissions.
        context
                .deleteFrom(PERMISSION_DOC)
                .where(PERMISSION_DOC.DOC_UUID.eq(docUuid))
                .execute();
    }

    private void addDocumentPermissions(final DSLContext context,
                                        final String sourceDocUuid,
                                        final String destDocUuid) {
        // Add permissions.
        context
                .insertInto(PERMISSION_DOC)
                .columns(PERMISSION_DOC.DOC_UUID,
                        PERMISSION_DOC.USER_UUID,
                        PERMISSION_DOC.PERMISSION_ID)
                .select(context
                        .select(DSL.val(destDocUuid),
                                PERMISSION_DOC_SOURCE.USER_UUID,
                                PERMISSION_DOC_SOURCE.PERMISSION_ID)
                        .from(PERMISSION_DOC_SOURCE)
                        .where(PERMISSION_DOC_SOURCE.DOC_UUID.eq(sourceDocUuid)))
                .onDuplicateKeyUpdate()
                .set(PERMISSION_DOC.PERMISSION_ID, PERMISSION_DOC.PERMISSION_ID)
                .execute();
    }

    @Override
    public void addDocumentCreatePermissions(String sourceDocUuid, String destDocUuid) {
        Objects.requireNonNull(sourceDocUuid, "Null source document UUID");
        Objects.requireNonNull(destDocUuid, "Null destination document UUID");

        // Add create permissions.
        JooqUtil.context(securityDbConnProvider, context ->
                addDocumentCreatePermissions(context, sourceDocUuid, destDocUuid));
    }

    @Override
    public void setDocumentCreatePermissions(final String sourceDocUuid, final String destDocUuid) {
        Objects.requireNonNull(sourceDocUuid, "Null source document UUID");
        Objects.requireNonNull(destDocUuid, "Null destination document UUID");

        JooqUtil.transaction(securityDbConnProvider, context -> {
            // Clear existing permssions.
            clearDocumentCreatePermissions(context, destDocUuid);
            // Add new permissions.
            addDocumentCreatePermissions(context, sourceDocUuid, destDocUuid);
        });
    }

    private void addDocumentCreatePermissions(final DSLContext context,
                                              final String sourceDocUuid,
                                              final String destDocUuid) {
        context
                .insertInto(PERMISSION_DOC_CREATE)
                .columns(PERMISSION_DOC_CREATE.DOC_UUID,
                        PERMISSION_DOC_CREATE.USER_UUID,
                        PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                .select(context
                        .select(DSL.val(destDocUuid),
                                PERMISSION_DOC_CREATE_SOURCE.USER_UUID,
                                PERMISSION_DOC_CREATE_SOURCE.DOC_TYPE_ID)
                        .from(PERMISSION_DOC_CREATE_SOURCE)
                        .where(PERMISSION_DOC_CREATE_SOURCE.DOC_UUID.eq(sourceDocUuid)))
                .onDuplicateKeyUpdate()
                .set(PERMISSION_DOC_CREATE.DOC_TYPE_ID, PERMISSION_DOC_CREATE.DOC_TYPE_ID)
                .execute();
    }

    private void clearDocumentCreatePermissions(final DSLContext context,
                                                final String docUuid) {
        // Clear create permissions.
        context
                .deleteFrom(PERMISSION_DOC_CREATE)
                .where(PERMISSION_DOC_CREATE.DOC_UUID.eq(docUuid))
                .execute();
    }

    @Override
    public ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(
            final FetchDocumentUserPermissionsRequest request) {
        Objects.requireNonNull(request, "Null request");
        Objects.requireNonNull(request.getDocRef(), "Null doc ref");

        final DocRef docRef = request.getDocRef();

        final int offset = JooqUtil.getOffset(request.getPageRequest());
        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);

        final List<Condition> conditions = new ArrayList<>();
        conditions.add(PERMISSION_DOC.DOC_UUID.eq(docRef.getUuid()));
        conditions.add(userDao.getUserCondition(request.getExpression()));

        final Collection<OrderField<?>> orderFields = userDao.createOrderFields(request);

        // If we have a single doc then try to deliver more useful permissions.
        final List<DocumentUserPermissions> list = JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(
                                PERMISSION_DOC.PERMISSION_ID,
                                STROOM_USER.UUID,
                                STROOM_USER.NAME,
                                STROOM_USER.DISPLAY_NAME,
                                STROOM_USER.FULL_NAME,
                                STROOM_USER.IS_GROUP)
                        .from(PERMISSION_DOC)
                        .leftOuterJoin(STROOM_USER).on(PERMISSION_DOC.USER_UUID.eq(STROOM_USER.UUID))
                        .where(conditions)
                        .orderBy(orderFields)
                        .offset(offset)
                        .limit(limit)
                        .fetch())
                .map(r -> {
                    final UserRef userRef = UserRef
                            .builder()
                            .uuid(r.get(STROOM_USER.UUID))
                            .subjectId(r.get(STROOM_USER.NAME))
                            .displayName(r.get(STROOM_USER.DISPLAY_NAME))
                            .fullName(r.get(STROOM_USER.FULL_NAME))
                            .group(r.get(STROOM_USER.IS_GROUP))
                            .build();
                    final DocumentPermission documentPermission = DocumentPermission.PRIMITIVE_VALUE_CONVERTER
                            .fromPrimitiveValue(r.get(PERMISSION_DOC.PERMISSION_ID).byteValue());
                    return new DocumentUserPermissions(userRef, documentPermission, null);
                });
        return ResultPage.createCriterialBasedList(list, request);


//        // Don't return any results if we have no docrefs to filter on.
//        if (docRefs != null && docRefs.isEmpty()) {
//            return ResultPage.empty();
//        }
//
//        final int offset = JooqUtil.getOffset(request.getPageRequest());
//        final int limit = JooqUtil.getLimit(request.getPageRequest(), true);
//
//        if (docRefs == null || docRefs.size() > 1) {
//            final Condition condition;
//            if (docRefs.size() > 1) {
//                condition = PERMISSION_DOC.DOC_UUID.in(docRefs
//                        .stream()
//                        .map(DocRef::getUuid)
//                        .collect(Collectors.toSet()));
//            } else {
//                condition = null;
//            }
//
//            // Find all users with permissions.
//            final List<DocumentUserPermissions> list = JooqUtil.contextResult(securityDbConnProvider, context ->
//            context
//                            .selectDistinct(
//                                    STROOM_USER.UUID,
//                                    STROOM_USER.NAME,
//                                    STROOM_USER.DISPLAY_NAME,
//                                    STROOM_USER.FULL_NAME,
//                                    STROOM_USER.IS_GROUP)
//                            .from(STROOM_USER)
//                            .leftOuterJoin(PERMISSION_DOC).on(PERMISSION_DOC.USER_UUID.eq(STROOM_USER.UUID))
//                            .where(condition)
//                            .orderBy(STROOM_USER.DISPLAY_NAME, STROOM_USER.NAME)
//                            .offset(offset)
//                            .limit(limit)
//                            .fetch())
//                    .map(r -> {
//                        final UserRef userRef = UserRef
//                                .builder()
//                                .uuid(r.get(STROOM_USER.UUID))
//                                .subjectId(r.get(STROOM_USER.NAME))
//                                .displayName(r.get(STROOM_USER.DISPLAY_NAME))
//                                .fullName(r.get(STROOM_USER.FULL_NAME))
//                                .group(r.get(STROOM_USER.IS_GROUP))
//                                .build();
//                        return new DocumentUserPermissions(userRef, null, null);
//                    });
//            return ResultPage.createCriterialBasedList(list, request);
//
//        } else {
//            final DocRef docRef = docRefs.iterator().next();
//
//            // If we have a single doc then try to deliver more useful permissions.
//            final List<DocumentUserPermissions> list = JooqUtil.contextResult(securityDbConnProvider, context ->
//            context
//                            .select(
//                                    PERMISSION_DOC.PERMISSION_ID,
//                                    STROOM_USER.UUID,
//                                    STROOM_USER.NAME,
//                                    STROOM_USER.DISPLAY_NAME,
//                                    STROOM_USER.FULL_NAME,
//                                    STROOM_USER.IS_GROUP)
//                            .from(PERMISSION_DOC)
//                            .leftOuterJoin(STROOM_USER).on(PERMISSION_DOC.USER_UUID.eq(STROOM_USER.UUID))
//                            .where(PERMISSION_DOC.DOC_UUID.eq(docRef.getUuid()))
//                            .orderBy(STROOM_USER.DISPLAY_NAME, STROOM_USER.NAME)
//                            .offset(offset)
//                            .limit(limit)
//                            .fetch())
//                    .map(r -> {
//                        final UserRef userRef = UserRef
//                                .builder()
//                                .uuid(r.get(STROOM_USER.UUID))
//                                .subjectId(r.get(STROOM_USER.NAME))
//                                .displayName(r.get(STROOM_USER.DISPLAY_NAME))
//                                .fullName(r.get(STROOM_USER.FULL_NAME))
//                                .group(r.get(STROOM_USER.IS_GROUP))
//                                .build();
//                        final DocumentPermission documentPermission = DocumentPermission.PRIMITIVE_VALUE_CONVERTER
//                                .fromPrimitiveValue(r.get(PERMISSION_DOC.PERMISSION_ID).byteValue());
//                        return new DocumentUserPermissions(userRef, documentPermission, null);
//                    });
//            return ResultPage.createCriterialBasedList(list, request);
//        }
    }

    //
//    @Override
//    public DocumentPermissionSet getPermissionsForDocumentForUser(final String docUuid,
//                                                                  final String userUuid) {
//        final Set<DocumentPermissionEnum> permissions = new HashSet<>();
//        final Set<String> folderCreatePermissions = new HashSet<>();
//
//        JooqUtil.contextResult(securityDbConnProvider, context -> context
//                        .select()
//                        .from(DOC_PERMISSION)
//                        .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
//                        .and(DOC_PERMISSION.DOC_UUID.eq(docUuid))
//                        .fetch())
//                .forEach(r -> {
//                    final String permission = r.get(DOC_PERMISSION.PERMISSION);
//                    if (permission.startsWith(DocumentPermissionSet.CREATE_PREFIX)) {
//                        final String docType = permission.substring(DocumentPermissionSet.CREATE_PREFIX.length());
//                        folderCreatePermissions.add(docType);
//                    } else {
//                        DocumentPermissionEnum.valueOf(permission);
//                    }
//                });
//        return new DocumentPermissionSet(permissions, folderCreatePermissions);
//    }
//
//    @Override
//    public UserDocumentPermissions getPermissionsForUser(final String userUuid) {
//        final UserDocumentPermissions userDocumentPermissions = new UserDocumentPermissions();
//
//        JooqUtil.contextResult(securityDbConnProvider, context -> context
//                        .selectDistinct(DOC_PERMISSION.DOC_UUID, DOC_PERMISSION.PERMISSION)
//                        .from(DOC_PERMISSION)
//                        .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
//                        .fetch())
//                .forEach(r -> {
//                    final String docUuid = r.get(DOC_PERMISSION.DOC_UUID);
//                    final String permission = r.get(DOC_PERMISSION.PERMISSION);
//                    if (permission.startsWith(DocumentPermissionSet.CREATE_PREFIX)) {
//                        final String documentType = permission
//                        .substring(DocumentPermissionSet.CREATE_PREFIX.length());
//                        userDocumentPermissions.addDocumentCreatePermission(docUuid, documentType);
//                    } else {
//                        final DocumentPermissionEnum permissionEnum = DocumentPermissionEnum.valueOf(permission);
//                        userDocumentPermissions.addPermission(docUuid, permissionEnum);
//                    }
//                });
//
//        return userDocumentPermissions;
//    }
//
//    @Override
//    public void addPermission(final String docRefUuid,
//    final String userUuid,
//    final DocumentPermissionEnum permission) {
//        final DocPermissionRecord rec = DOC_PERMISSION.newRecord();
//        rec.setDocUuid(docRefUuid);
//        rec.setUserUuid(userUuid);
//        rec.setPermission(permission.name());
//
//        // Don't complain if we insert a perm record that we already have.
//        JooqUtil.context(securityDbConnProvider, context ->
//                JooqUtil.tryCreate(context, rec));
//    }
//
//    @Override
//    public void removePermission(final String docRefUuid,
//                                 final String userUuid,
//                                 final DocumentPermissionEnum permission) {
//        JooqUtil.context(securityDbConnProvider, context -> context
//                .deleteFrom(DOC_PERMISSION)
//                .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
//                .and(DOC_PERMISSION.DOC_UUID.equal(docRefUuid))
//                .and(DOC_PERMISSION.PERMISSION.equalIgnoreCase(permission.name()))
//                .execute()
//        );
//    }
//
//    @Override
//    public void addFolderCreatePermission(final String docRefUuid, final String userUuid, final String documentType) {
//        final DocPermissionRecord rec = DOC_PERMISSION.newRecord();
//        rec.setDocUuid(docRefUuid);
//        rec.setUserUuid(userUuid);
//        rec.setPermission(DocumentPermissionSet.CREATE_PREFIX + documentType);
//
//        // Don't complain if we insert a perm record that we already have.
//        JooqUtil.context(securityDbConnProvider, context ->
//                JooqUtil.tryCreate(context, rec));
//    }
//
//    @Override
//    public void removeFolderCreatePermission(final String docRefUuid,
//                                             final String userUuid,
//                                             final String documentType) {
//        JooqUtil.context(securityDbConnProvider, context -> context
//                .deleteFrom(DOC_PERMISSION)
//                .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
//                .and(DOC_PERMISSION.DOC_UUID.equal(docRefUuid))
//                .and(DOC_PERMISSION.PERMISSION.equalIgnoreCase(DocumentPermissionSet.CREATE_PREFIX + documentType))
//                .execute()
//        );
//    }
//
//    @Override
//    public void addPermissions(final String docRefUuid,
//                               final String userUuid,
//                               final DocumentPermissionSet permissions) {
//        permissions.getPermissions().forEach(permission ->
//                addPermission(docRefUuid, userUuid, permission));
//        permissions.getDocumentCreatePermissions().forEach(docType ->
//                addFolderCreatePermission(docRefUuid, userUuid, docType));
//    }
//
//    @Override
//    public void removePermissions(final String docRefUuid,
//                                  final String userUuid,
//                                  final DocumentPermissionSet permissions) {
//        if (permissions != null && !permissions.isEmpty()) {
//            final Set<String> permissionNames = new HashSet<>();
//            permissions
//                    .getPermissions()
//                    .stream()
//                    .map(DocumentPermissionEnum::name)
//                    .forEach(permissionNames::add);
//            permissions
//                    .getDocumentCreatePermissions()
//                    .stream()
//                    .map(name -> DocumentPermissionSet.CREATE_PREFIX + name)
//                    .forEach(permissionNames::add);
//
//            Objects.requireNonNull(docRefUuid);
//            Objects.requireNonNull(userUuid);
//            JooqUtil.context(securityDbConnProvider, context -> context
//                    .deleteFrom(DOC_PERMISSION)
//                    .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
//                    .and(DOC_PERMISSION.DOC_UUID.equal(docRefUuid))
//                    .and(DOC_PERMISSION.PERMISSION.in(permissionNames))
//                    .execute()
//            );
//        }
//    }
//
//    @Override
//    public void clearDocumentPermissionsForUser(String docRefUuid, String userUuid) {
//        JooqUtil.context(securityDbConnProvider, context -> context
//                .deleteFrom(DOC_PERMISSION)
//                .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
//                .and(DOC_PERMISSION.DOC_UUID.equal(docRefUuid))
//                .execute()
//        );
//    }
//
//    @Override
//    public void clearDocumentPermissionsForDoc(final String docRefUuid) {
//        JooqUtil.context(securityDbConnProvider, context -> context
//                .deleteFrom(DOC_PERMISSION)
//                .where(DOC_PERMISSION.DOC_UUID.equal(docRefUuid))
//                .execute()
//        );
//    }
//
//    @Override
//    public void clearDocumentPermissionsForDocs(final Set<String> docRefUuids) {
//        clearDocumentPermissionsForDocs(docRefUuids, IN_LIST_LIMIT);
//    }
//
//    // Pkg private to aid testing with a smaller batch size
//    void clearDocumentPermissionsForDocs(final Set<String> docRefUuids,
//                                         final int batchSize) {
//        if (NullSafe.hasItems(docRefUuids)) {
//            LOGGER.debug(() -> LogUtil.message("Deleting permission records for {} proc filter UUIDs",
//                    docRefUuids.size()));
//            final List<String> workingList = new ArrayList<>(docRefUuids);
//            final AtomicInteger totalCnt = new AtomicInteger();
//            JooqUtil.transaction(securityDbConnProvider, txnContext -> {
//                while (!workingList.isEmpty()) {
//                    // The in clause has limits so do a batch at a time. A view on workingList
//                    final List<String> subListView = workingList.subList(
//                            0,
//                            Math.min(workingList.size(), batchSize));
//                    if (!subListView.isEmpty()) {
//                        final int cnt = txnContext.deleteFrom(DOC_PERMISSION)
//                                .where(DOC_PERMISSION.DOC_UUID.in(subListView))
//                                .execute();
//                        totalCnt.addAndGet(cnt);
//                        LOGGER.debug(() -> LogUtil.message(
//                                "Deleted {} doc_permission records for a batch of {} doc UUIDs",
//                                cnt, subListView.size()));
//                        // Now clear limitedListView which clears the items from workingList
//                        subListView.clear();
//                    }
//                }
//            });
//            LOGGER.debug(() -> LogUtil.message("Deleted a total of {} doc_permission records for {} doc UUIDs",
//                    totalCnt, docRefUuids.size()));
//        }
//    }
}
