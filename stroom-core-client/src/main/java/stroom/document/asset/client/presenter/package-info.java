/**
 * <p>This package contains the Document Asset Manager, which provides a tree-based file and folder management system
 * for arbitrary assets associated with a Stroom document. These assets are stored in the database and can be
 * retrieved via a servlet for use in the browser (e.g., CSS, JS, images).</p>
 *
 * <h2>Integration Guide</h2>
 * <p>To integrate the Document Asset Manager into a new or existing document type, follow these steps:</p>
 *
 * <h3>1. Dependency Injection</h3>
 * <p>Inject a {@link stroom.document.asset.client.component.DocumentAssetsTabController} into your
 * document's editor (a <code>DocumentEditor</code>), which owns it. Ensure it is parameterized with
 * your document's shared POJO type.</p>
 * <pre>
 * private final DocumentAssetsTabController&lt;MyDoc&gt; assetsTab;
 *
 * &#64;Inject
 * public MyDocEditor(..., DocumentAssetsTabController&lt;MyDoc&gt; assetsTab, ...) {
 *     this.assetsTab = own(assetsTab);
 * }
 * </pre>
 *
 * <h3>2. Add the Asset Tab</h3>
 * <p>Add the controller as a tab the editor owns already; the tab adds its buttons to the document's
 * toolbar. The assets are saved after the document (step 5), so the tab writes nothing.</p>
 * <pre>
 * private static final TabData ASSETS = new TabDataImpl("Assets");
 * ...
 * addOwnedTab(ASSETS, assetsTab, DocumentAssetsTabController::read);
 * </pre>
 *
 * <h3>3. Synchronize Tab Selection</h3>
 * <p>Override <code>afterSelectTab</code> to ensure the editor re-evaluates its state when the
 * asset tab is selected.</p>
 * <pre>
 * &#64;Override
 * protected void afterSelectTab(final TabData tab) {
 *     if (ASSETS.equals(tab)) {
 *         onChange();
 *     }
 * }
 * </pre>
 *
 * <h3>4. Aggregate Dirty State</h3>
 * <p>Ensure the editor considers the asset manager's dirty state when determining if the document
 * as a whole has unsaved changes.</p>
 * <pre>
 * &#64;Override
 * protected boolean hasAssociatedDirty() {
 *     return assetsTab.isDirty();
 * }
 * </pre>
 *
 * <h3>5. Hook into the Save Lifecycle</h3>
 * <p>Provide callbacks for the standard <b>Save</b> and <b>Save As</b> operations by
 * overriding the respective callback methods.</p>
 * <pre>
 * &#64;Override
 * public BiConsumer&lt;MyDoc, Consumer&lt;MyDoc&gt;&gt; getPostSaveCallback() {
 *     return assetsTab::onSave;
 * }
 *
 * &#64;Override
 * public BiConsumer&lt;MyDoc, Consumer&lt;MyDoc&gt;&gt; getPostSaveAsCallback() {
 *     return assetsTab::onSaveAs;
 * }
 * </pre>
 *
 * <h3>6. Update the Plugin</h3>
 * <p>By default, <code>DocumentPlugin</code> does not support post-save callbacks. You must
 * override the 6-argument <code>save</code> method in your <code>DocumentPlugin</code>
 * subclass to execute the callback after the main document has been successfully saved.</p>
 * <pre>
 * &#64;Override
 * public void save(final DocRef docRef,
 *                  final MyDoc document,
 *                  final BiConsumer&lt;MyDoc, Consumer&lt;MyDoc&gt;&gt; postSaveCallback,
 *                  final Consumer&lt;MyDoc&gt; resultConsumer,
 *                  final RestErrorHandler errorHandler,
 *                  final TaskMonitorFactory taskMonitorFactory) {
 *     restFactory
 *             .create(MY_RESOURCE)
 *             .method(res -&gt; res.update(document.getUuid(), document))
 *             .onSuccess(doc -&gt; {
 *                 if (postSaveCallback != null) {
 *                     postSaveCallback.accept(doc, resultConsumer);
 *                 } else {
 *                     resultConsumer.accept(doc);
 *                 }
 *             })
 *             .onFailure(errorHandler)
 *             .taskMonitorFactory(taskMonitorFactory)
 *             .exec();
 * }
 * </pre>
 */
package stroom.document.asset.client.presenter;
