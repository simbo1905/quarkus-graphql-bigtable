package com.github.simbo1905.bigtablegraphql;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminSettings;
import com.google.cloud.bigtable.admin.v2.models.CreateTableRequest;
import com.google.cloud.bigtable.admin.v2.models.ModifyColumnFamiliesRequest;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowCell;
import com.google.cloud.bigtable.data.v2.models.RowMutation;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Please see https://cloud.google.com/bigtable/docs/schema-design
 * They recommend one table, not two, so we have one entity table
 * and put each entity in a different column family.
 * Note in the real world we might do what they recommend of prefixing
 * the key with the entity name or something else to optimise
 * for range queries but in this demo we have unique IDs across
 * entities and just fetch by ID so we are "keeping it simple".
 */
@Slf4j
public class BigtableInitializer {
    private final BigtableDataClient dataClient;
    private final BigtableTableAdminClient adminClient;

    private static List<Map<String, String>> books = Arrays.asList(
            ImmutableMap.of("id", "book-1",
                    "name", "Harry Potter and the Philosopher's Stone",
                    "pageCount", "223",
                    "authorId", "author-1"),
            ImmutableMap.of("id", "book-2",
                    "name", "Moby Dick",
                    "pageCount", "635",
                    "authorId", "author-2"),
            ImmutableMap.of("id", "book-3",
                    "name", "Interview with the vampire",
                    "pageCount", "371",
                    "authorId", "author-3")
    );

    private static List<Map<String, String>> authors = Arrays.asList(
            ImmutableMap.of("id", "author-1",
                    "firstName", "Joanne",
                    "lastName", "Rowling"),
            ImmutableMap.of("id", "author-2",
                    "firstName", "Herman",
                    "lastName", "Melville"),
            ImmutableMap.of("id", "author-3",
                    "firstName", "Anne",
                    "lastName", "Rice")
    );

    /** Demonstrates how to create a table. */
    public void createTable(final String tableId, final String colFamily) {
        // [START bigtable_hw_create_table_veneer]
        // Checks if table exists, creates table if does not exist.
        if (!adminClient.exists(tableId)) {
            log.info("Creating table: " + tableId);
            CreateTableRequest createTableRequest =
                    CreateTableRequest.of(tableId).addFamily(colFamily);
            adminClient.createTable(createTableRequest);
            log.info("Table {} created successfully", tableId);
        } else {
            log.warn("Table {} already exists", tableId);
        }
        // [END bigtable_hw_create_table_veneer]
    }

    /** Demonstrates how to write some rows to a table. */
    public void writeToTable(final String tableId,
                             final String colFamily,
                             final String key,
                             final Map<String,String> values) {
        try {
                RowMutation rowMutation =
                        RowMutation.create(tableId, key);
                for( Map.Entry<String, String> e : values.entrySet()){
                    rowMutation = rowMutation.setCell(colFamily, e.getKey(), e.getValue());
                }
                dataClient.mutateRow(rowMutation);

        } catch (NotFoundException e) {
            log.error("Failed to write to non-existent table {}: ", tableId, e.getMessage());
            throw e;
        }
    }

    /** Demonstrates how to read a single row from a table. */
    public Row readSingleRow(final String tableId, final String key) {
        // [START bigtable_hw_get_by_key_veneer]
        try {
            System.out.println("\nReading a single row by row key");
            Row row = dataClient.readRow(tableId, key);
            System.out.println("Row: " + row.getKey().toStringUtf8());
            for (RowCell cell : row.getCells()) {
                log.info(
                        "Read back - Table: {}, Family: {}, Qualifier: {}, Value: {}",
                        tableId,
                        cell.getFamily(),
                        cell.getQualifier().toStringUtf8(),
                        cell.getValue().toStringUtf8());
            }
            return row;
        } catch (NotFoundException e) {
            log.error("Failed to read key {} from a non-existent table {} : {}", key, tableId, e.getMessage());
            return null;
        }
        // [END bigtable_hw_get_by_key_veneer]
    }

    public static void main(String[] args) throws Exception {
        final Properties props = new Properties();
        final URL url = Resources.getResource("application.properties");
        props.load(url.openStream());

        final String projectId = String.valueOf(props.get("gcp.project-id"));
        final String instanceId = String.valueOf(props.get("gcp.bigtable-instance"));

        if(projectId == null) throw new AssertionError("gcp.project-id must not be null");
        if(instanceId == null) throw new AssertionError("gcp.bigtable-instance must not be null");

        (new BigtableInitializer(projectId, instanceId)).run();
    }

    private void run() {
        createTable("entity", "book");
        for( final Map<String,String> book : books) {
            final String key = book.get("id");
            writeToTable("entity", "book", key, book);
            readSingleRow("entity", key);
        }

        addColumnFamily("entity", "author");
        for( final Map<String,String> author : authors) {
            final String key = author.get("id");
            writeToTable("entity", "author", key, author);
            readSingleRow("entity", key);
        }
    }

    private void addColumnFamily(String tableId, String colFamily) {
        // [START bigtable_hw_create_table_veneer]
        // Checks if table exists, creates table if does not exist.
        if (adminClient.exists(tableId)) {
            log.info("Creating family {} in table {}", colFamily, tableId);
            ModifyColumnFamiliesRequest request =
                    ModifyColumnFamiliesRequest.of(tableId).addFamily(colFamily);
            adminClient.modifyFamilies(request);
            log.info("Table {} created successfully", tableId);
        } else {
            log.error("table does not exist: {}", tableId);
        }
        // [END bigtable_hw_create_table_veneer]
    }


    @SneakyThrows
    public BigtableInitializer(String projectId, String instanceId) {
        // [START bigtable_hw_connect_veneer]
        // Creates the settings to configure a bigtable data client.
        BigtableDataSettings settings =
                BigtableDataSettings.newBuilder().setProjectId(projectId).setInstanceId(instanceId).build();

        // Creates a bigtable data client.
        dataClient = BigtableDataClient.create(settings);

        // Creates the settings to configure a bigtable table admin client.
        BigtableTableAdminSettings adminSettings =
                BigtableTableAdminSettings.newBuilder()
                        .setProjectId(projectId)
                        .setInstanceId(instanceId)
                        .build();

        // Creates a bigtable table admin client.
        adminClient = BigtableTableAdminClient.create(adminSettings);
        // [END bigtable_hw_connect_veneer]
    }
}
