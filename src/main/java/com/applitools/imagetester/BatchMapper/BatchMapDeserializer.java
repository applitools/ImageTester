package com.applitools.imagetester.BatchMapper;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class BatchMapDeserializer {
    public static List<BatchMapPojo> readFile(String csvFileName) throws IOException {
        File csvFile = new File(csvFileName);
        CsvMapper mapper = new CsvMapper();
        CsvSchema csvSchema = mapper
                .typedSchemaFor(BatchMapPojo.class)
                .withHeader()
                .withColumnSeparator('|')
                .withComments();
        MappingIterator<BatchMapPojo> batchMapPojoMappingIterator = mapper
                .readerWithTypedSchemaFor(BatchMapPojo.class)
                .with(csvSchema)
                .readValues(csvFile);
        return batchMapPojoMappingIterator.readAll();
    }
}