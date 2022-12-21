package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.api.ImportConverter;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;

import java.util.Map;
import javax.inject.Inject;

@Deprecated
class ImportConverterImpl implements ImportConverter {

    private final Map<DataMapConverterBinder.ConverterType, DataMapConverter> converterMap;

    @Inject
    ImportConverterImpl(final Map<DataMapConverterBinder.ConverterType, DataMapConverter> converterMap) {
        this.converterMap = converterMap;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportSettings importSettings,
                                       final String userId) {
        final DataMapConverter converter = converterMap.get(new DataMapConverterBinder.ConverterType(docRef.getType()));
        if (converter != null) {
            return converter.convert(docRef, dataMap, importState, importSettings, userId);
        }
        return dataMap;
    }
}
