package stroom.pipeline.filter;

import stroom.docref.DocRef;
import stroom.docstore.api.HasFindByName;
import stroom.docstore.shared.Doc;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.util.io.PathCreator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class DocFinder<D extends Doc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocFinder.class);

    private final String type;
    private final PathCreator pathCreator;
    private final HasFindByName hasFindByName;

    public DocFinder(final String type,
                     final PathCreator pathCreator,
                     final HasFindByName hasFindByName) {
        this.type = type;
        this.pathCreator = pathCreator;
        this.hasFindByName = hasFindByName;
    }

    public DocRef findDoc(final DocRef defaultRef,
                          final String namePattern,
                          final String feedName,
                          final String pipelineName,
                          final Consumer<String> errorConsumer,
                          final boolean suppressNotFoundWarnings) {
        DocRef doc = null;

        // Load the document from a name pattern if one has been specified.
        if (namePattern != null && namePattern.trim().length() > 0) {
            // Resolve replacement variables.
            String resolvedName = namePattern.trim();
            if (feedName != null) {
                resolvedName = pathCreator.replace(resolvedName, "feed", () -> feedName);
            }
            if (pipelineName != null) {
                resolvedName = pathCreator.replace(resolvedName, "pipeline", () -> pipelineName);
            }

            // Make sure there are no replacement vars left.
            final String[] vars = pathCreator.findVars(resolvedName);
            if (vars.length > 0) {
                final StringBuilder sb = new StringBuilder();
                sb.append(type);
                sb.append(" name pattern \"");
                sb.append(namePattern);
                sb.append("\" contains invalid replacement variables (");
                for (final String var : vars) {
                    sb.append(var);
                    sb.append(", ");
                }
                sb.setLength(sb.length() - 2);
                sb.append(")");
                throw new ProcessException(sb.toString());
            }

            LOGGER.debug("Finding " + type + " with resolved name '{}' from pattern '{}'", resolvedName, namePattern);
            final List<DocRef> docs = hasFindByName.findByName(resolvedName);
            if (docs == null || docs.size() == 0) {
                if (errorConsumer != null && !suppressNotFoundWarnings) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("No ");
                    sb.append(type);
                    sb.append(" found with name '");
                    sb.append(resolvedName);
                    sb.append("' from pattern '");
                    sb.append(namePattern);

                    if (defaultRef != null) {
                        sb.append("' - using default '");
                        sb.append(defaultRef.getName());
                        sb.append("'");
                    } else {
                        sb.append("' - no default specified");
                    }

                    errorConsumer.accept(sb.toString());
                }
            } else {
                doc = docs.get(0);
                if (errorConsumer != null && docs.size() > 1) {
                    final String message = "" +
                            "Multiple " + type + " found with name '" +
                            resolvedName +
                            "' from pattern '" +
                            namePattern +
                            "' - using " + type + " with uuid (" +
                            doc.getUuid() +
                            ")";
                    errorConsumer.accept(message);
                }
            }
        }

        // Load the document from a reference if we haven't found it by name.
        if (doc == null && defaultRef != null) {
            doc = defaultRef;
            if (doc == null) {
                final String message = "" +
                        type + " \"" +
                        defaultRef.getName() +
                        "\" appears to have been deleted";
                throw new ProcessException(message);
            }
        }

        return doc;
    }
}
