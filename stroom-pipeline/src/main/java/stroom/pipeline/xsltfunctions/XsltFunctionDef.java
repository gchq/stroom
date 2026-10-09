package stroom.pipeline.xsltfunctions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface XsltFunctionDef {

    /// The name of the function
    String name();

    /// The html link anchor to the section of the documentation page for this function.
    /// It should only need to be set if the name when converted into anchor format differs
    /// from the actual anchor.
    String helpAnchor() default "";

    /// Any alias names for the function
    String[] aliases() default {};

    /// The single category of functions that this function signature belongs to unless overridden at the
    /// signature level.
    /// Defined as an array to allow us to not have one by default.
    XsltFunctionCategory[] commonCategory() default {};

    /// An array of sub-categories that this function belongs to. The sub-categories represent a path
    /// in a tree of categories from root to leaf. E.g. if the main category is String the sub categories
    /// could be [Conversion, Case], i.e. String -> Conversion -> Case.
    /// Can be overridden at the signature level.
    String[] commonSubCategories() default {};

    /// A description of what the function does that is common to all signatures unless overridden
    /// at the signature level. This should be a few sentences at most as it will be displayed in
    /// hover tooltips in stroom-docs. If you need a big description, either use the
    /// extendedCommonDescription or add content directly to stroom-docs.
    ///
    /// You must provide a commonDescription even if each signature provides its own description.
    /// This is because stroom-docs needs a common description for some of its content.
    ///
    /// The description is assumed to be Markdown.
    String commonDescription() default "";

    /// Optional extended description that is common to all signatures. It is in addition to the
    /// commonDescription. It can be used when the description is more than a few sentences.
    ///
    /// The description is assumed to be Markdown.
    String extendedCommonDescription() default "";

    /// A single return type that is common to all signatures unless overridden at the signature level.
    /// You must specify either this or [XsltFunctionSignature#returnType()]
    /// Defined as an array to allow to be optional.
    XsltDataType[] commonReturnType() default {};

    /// A return description that is common to all signatures unless overridden at the signature level
    /// You must specify either this or [XsltFunctionSignature#returnDescription()]
    ///
    /// The description is assumed to be Markdown
    String commonReturnDescription() default "";

    /// All the overloaded function signatures for the method,
    /// e.g. parseDate(dateStr) & parseDate(dateStr, format).
    /// Must have at least one signature.
    XsltFunctionSignature[] signatures();
}
