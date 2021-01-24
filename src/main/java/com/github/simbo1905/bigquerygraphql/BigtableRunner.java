package com.github.simbo1905.bigquerygraphql;


import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowCell;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class BigtableRunner {

    BigtableDataClient dataClient;

    @ConfigProperty(name = "gcp.project-id")
    String projectId;

    @ConfigProperty(name = "gcp.big-table-instance", defaultValue = "bigtable-graphql")
    String instanceId;

    @ConfigProperty(name = "gcp.bigquery.log.thresholdms", defaultValue = "100")
    long logThresholdMs = 100;

    @SneakyThrows
    @PostConstruct
    protected void init() {
        log.info("Initialising BigTable for project {}", projectId);
        BigtableDataSettings settings =
                BigtableDataSettings.newBuilder()
                        .setProjectId(projectId)
                        .setInstanceId(instanceId)
                        .build();

        // Creates a bigtable data client.
        dataClient = BigtableDataClient.create(settings);
    }

    @PreDestroy
    protected void close(){
        dataClient.close();
    }

    /**
     * This method copies the source attribute to the dest attribute as a BQ parameter value map.
     * It current assumes you want to query by a String. It should be extended to query by other types.
     * It resolves the source attribute from the DataFetchingEnvironment by first checking if there is
     * a source entity. If there is a source it calls <pre>entity.get(sourceAttr)</pre>. If there is no
     * source entity it calls <pre>dataFetchingEnvironment.getArgument(sourceAttr)</pre>
     *
     * @param dataFetchingEnvironment The context of where the query is being invoked.
     * @param sourceAttr The name of the source attribute e.g., "id" or "authorId".`
     * @return A BQ named parameters map.
     */
    private String resolveQueryParams(DataFetchingEnvironment dataFetchingEnvironment,
                                                                String sourceAttr) {
        Map<String, String> entity = dataFetchingEnvironment.getSource();
        String id = (entity != null) ? entity.get(sourceAttr) : dataFetchingEnvironment.getArgument(sourceAttr);
        return id;
    }

    public DataFetcher queryForOne(String tableId, String family, Set<String> qualifiers, String sourceAttr) {
        // return a lambda that runs the query and returns as a GraphQL friendly Map or null
        return dataFetchingEnvironment -> {
            final String key = resolveQueryParams(dataFetchingEnvironment, sourceAttr);
            final Map<String, String> result = new HashMap<>();
            final long startTime = System.currentTimeMillis();

            final String qs = qualifiers.stream().collect(Collectors.joining(","));
            log.trace("table={}, key={}, sourceAttr={}, family={}, qualifier=[{}]",
                    tableId,
                    key,
                    sourceAttr,
                    qs);

            try {
                final Row row = dataClient.readRow(tableId, key);
                for (RowCell cell : row.getCells()) {
                    final String qualifier = cell.getQualifier().toStringUtf8();
                    if( cell.getFamily().equals(family) && qualifiers.contains(qualifier) ) {
                        result.put(qualifier, cell.getValue().toStringUtf8());
                    }
                }
            } catch (NotFoundException e) {
                log.trace("row not found. table={}, key={}", tableId, key);
                return null;
            } finally {
                // you probably want to use some other metrics/gauges to understand actual performance.
                final float duration = (System.currentTimeMillis() - startTime);
                if( duration > logThresholdMs ) {
                    log.info("slow query ms={}, table={}, key={}, sourceAttr={}, family={}, qualifier=[{}]",
                            Math.round(duration),
                            tableId,
                            key,
                            sourceAttr,
                            qs);
                }
            }
            return result;
        };
    }
}
