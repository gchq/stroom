package stroom.util.filter;

import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.shared.filter.FilterFieldDefinition;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class QuickFilterTestBed {

    /**
     * This is a handy little interactive testbed for the filtering/ranking. It uses a list of all the
     * fully qualified class names visible to this test. You can type the term(s) then see what results come out
     * e.g. 'map hash sig:serial'
     */
    public static void main(String[] args) {

        FilterFieldMappers<ClassInfo> filterFieldMappers = FilterFieldMappers.of(
                FilterFieldMapper.of(
                        FilterFieldDefinition.defaultField("name"),
                        ClassInfo::getName),
                FilterFieldMapper.of(
                        FilterFieldDefinition.qualifiedField("package"),
                        ClassInfo::getPackageName),
                FilterFieldMapper.of(
                        FilterFieldDefinition.qualifiedField("sig"),
                        ClassInfo::getTypeSignatureStr));

        List<ClassInfo> classInfoList;
        try (ScanResult result = new ClassGraph()
                .whitelistPackages("stroom")
                .enableClassInfo()
                .ignoreClassVisibility()
                .scan()) {

            classInfoList = new ArrayList<>(result.getAllClasses());
        }

        final Scanner scanner = new Scanner(System.in);
        do {
            System.out.println("QuickFilter Test Bed - filtering fully qualified class names");
            System.out.println("Enter your search term, then hit enter to see the results:");
            System.out.println("Valid field qualifiers: "
                    + filterFieldMappers.getFieldQualifiers().stream().sorted().collect(Collectors.toList()));
            final String userInput = scanner.nextLine();

            if (userInput.equals("quit")
                    || userInput.equals("exit")
                    || userInput.equals(":q")) {
                System.out.println("Exiting");
                break;
            }

            final List<ClassInfo> filteredClassInfoList = QuickFilterPredicateFactory.filterStream(
                            userInput,
                            filterFieldMappers,
                            classInfoList.stream())
                    .collect(Collectors.toList());

            final String outputStr = AsciiTable.builder(filteredClassInfoList)
                    .withColumn(Column.of("name", ClassInfo::getName))
                    .withColumn(Column.of("package", ClassInfo::getPackageName))
                    .withColumn(Column.of("sig", ClassInfo::getTypeSignatureStr))
                    .withRowLimit(20)
                    .build();

            System.out.println("For user input  '" + userInput +
                    "', results [" + filteredClassInfoList.size() + "]:\n" + outputStr);
        } while (scanner.hasNext());

        System.exit(0);
    }

}
