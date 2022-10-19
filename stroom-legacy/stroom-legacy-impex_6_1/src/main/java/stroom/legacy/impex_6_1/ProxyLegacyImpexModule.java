package stroom.legacy.impex_6_1;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.importexport.api.ImportConverter;

import com.google.inject.AbstractModule;

// TODO: 19/10/2022 Remove as not used
@Deprecated
public class ProxyLegacyImpexModule extends AbstractModule {

    @Override
    protected void configure() {
        // This is pretty much a copy of LegacyImpexModule but with only the
        // doc types that proxy cares about.
        bind(ImportConverter.class).to(ImportConverterImpl.class);
        DataMapConverterBinder.create(binder())
                .bind(DictionaryDoc.DOCUMENT_TYPE, DictionaryDataMapConverter.class);
    }
}
