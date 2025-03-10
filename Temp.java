import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustAllStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() throws Exception {
        // 1. Build SSLContext (INSECURE for testing)
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                .build();

        // 2. Configure Hostname Verifier (bypass for testing)
        HostnameVerifier hostnameVerifier = (hostname, session) -> true;

        // 3. Build HttpClient with TlsStrategy
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(hostnameVerifier)
                .setTlsVersions("TLSv1.3", "TLSv1.2") // Protocols
                .evictExpiredConnections()
                .build();

        // 4. Create Request Factory
        ClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}




import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class ElasticSearchOrderDetailsRepository {

    private static final Logger log = LoggerFactory.getLogger(ElasticSearchOrderDetailsRepository.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String INDEX_NAME = "order_details_alias"; // Replace with actual alias

    public ElasticSearchOrderDetailsRepository(RestClient restClient) {
        this.restClient = restClient;
    }

    private List<ElasticSearchOrderDetail> executeSearch(String jsonQuery) {
        try {
            Request request = new Request("POST", "/" + INDEX_NAME + "/_search");
            request.setJsonEntity(jsonQuery);

            Response response = restClient.performRequest(request);
            String responseBody = EntityUtils.toString(response.getEntity());

            JsonNode hits = objectMapper.readTree(responseBody).path("hits").path("hits");
            return objectMapper.convertValue(hits, objectMapper.getTypeFactory().constructCollectionType(List.class, ElasticSearchOrderDetail.class));

        } catch (Exception e) {
            log.error("Error executing Elasticsearch query: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public List<ElasticSearchOrderDetail> findByServiceOrderId(String serviceOrderId) {
        String query = String.format("""
            {
                "query": { "term": { "serviceOrderId.keyword": "%s" } }
            }
        """, serviceOrderId);
        return executeSearch(query);
    }

    public List<ElasticSearchOrderDetail> findByNspeId(String nspeId) {
        String query = String.format("""
            {
                "query": { "term": { "nspeId.keyword": "%s" } }
            }
        """, nspeId);
        return executeSearch(query);
    }

    public List<ElasticSearchOrderDetail> findByPremisysQuoteId(String premisysQuoteId) {
        String query = String.format("""
            {
                "query": { "term": { "premisysQuoteId.keyword": "%s" } }
            }
        """, premisysQuoteId);
        return executeSearch(query);
    }

    public List<ElasticSearchOrderDetail> findByOrderRequestId(String orderRequestId) {
        String query = String.format("""
            {
                "query": { "term": { "orderRequestId.keyword": "%s" } }
            }
        """, orderRequestId);
        return executeSearch(query);
    }

    public List<ElasticSearchOrderDetail> findByTinAndOrderId(String tin, String orderId) {
        String query = String.format("""
            {
                "query": {
                    "bool": {
                        "must": [
                            { "term": { "tin.keyword": "%s" } },
                            { "term": { "orderId.keyword": "%s" } }
                        ]
                    }
                }
            }
        """, tin, orderId);
        return executeSearch(query);
    }

    public List<ElasticSearchOrderDetail> findByTin(String tin) {
        String query = String.format("""
            {
                "query": { "term": { "tin.keyword": "%s" } }
            }
        """, tin);
        return executeSearch(query);
    }

    public Optional<ElasticSearchOrderDetail> findById(String id) {
        String query = String.format("""
            {
                "query": { "term": { "_id": "%s" } }
            }
        """, id);
        List<ElasticSearchOrderDetail> results = executeSearch(query);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}





ELASTICSEARCH_INDEX_DELETE_ENABLED
elasticsearch.index.delete.enabled=true

    if (!isDeleteEnabled) {
            log.info("Index deletion is disabled. Skipping deletion for index: {}", oldIndex);
            return;
        }
@Value("${elasticsearch.index.delete.enabled:false}")
    private boolean isDeleteEnabled;

package com.example.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ElasticSearchOrderDetailsRestRepository {

    private static final Logger log = LoggerFactory.getLogger(ElasticSearchOrderDetailsRestRepository.class);

    // Use the alias or index name that your documents are stored in.
    private static final String INDEX_NAME = "order_details_alias";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ElasticSearchOrderDetailsRestRepository(RestClient restClient) {
        this.restClient = restClient;
        this.objectMapper = new ObjectMapper();
    }

    public ElasticSearchOrderDetail findById(String id) {
        try {
            Request request = new Request("GET", "/" + INDEX_NAME + "/_doc/" + id);
            Response response = restClient.performRequest(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                log.info("Document with id {} not found in index {}", id, INDEX_NAME);
                return null;
            }
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode sourceNode = root.path("_source");
            if (sourceNode.isMissingNode()) {
                log.warn("No _source found for document id {}", id);
                return null;
            }
            return objectMapper.treeToValue(sourceNode, ElasticSearchOrderDetail.class);
        } catch (IOException e) {
            log.error("Error occurred while fetching document by id {}: {}", id, e.getMessage());
            return null;
        }
    }

    public List<ElasticSearchOrderDetail> findByServiceOrderId(String serviceOrderId) {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"term\": { \"serviceOrderId\": \"" + serviceOrderId + "\" }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson, "findByServiceOrderId", serviceOrderId);
    }

    public List<ElasticSearchOrderDetail> findByNspeId(String nspeId) {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"term\": { \"nspeId\": \"" + nspeId + "\" }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson, "findByNspeId", nspeId);
    }

    public List<ElasticSearchOrderDetail> findByPremisysQuoteId(String premisysQuoteId) {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"term\": { \"premisysQuoteId\": \"" + premisysQuoteId + "\" }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson, "findByPremisysQuoteId", premisysQuoteId);
    }

    public List<ElasticSearchOrderDetail> findByOrderRequestId(String orderRequestId) {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"term\": { \"orderRequestId\": \"" + orderRequestId + "\" }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson, "findByOrderRequestId", orderRequestId);
    }

    public List<ElasticSearchOrderDetail> findByTinAndOrderId(String tin, String orderId) {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"must\": [\n" +
                "        { \"term\": { \"tin\": \"" + tin + "\" } },\n" +
                "        { \"term\": { \"orderId\": \"" + orderId + "\" } }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson, "findByTinAndOrderId", tin + ", " + orderId);
    }

    public List<ElasticSearchOrderDetail> findByTin(String tin) {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"term\": { \"tin\": \"" + tin + "\" }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson, "findByTin", tin);
    }

    /**
     * Executes a search query against the specified index using the given JSON query.
     * This method handles exceptions and logs errors instead of throwing.
     *
     * @param queryJson  the JSON DSL query string.
     * @param methodName the name of the calling method for logging purposes.
     * @param criteria   the search criteria value used.
     * @return list of matching ElasticSearchOrderDetail objects, or an empty list if an error occurs.
     */
    private List<ElasticSearchOrderDetail> executeSearch(String queryJson, String methodName, String criteria) {
        List<ElasticSearchOrderDetail> results = new ArrayList<>();
        try {
            log.info("Executing {} with criteria: {}", methodName, criteria);
            Request request = new Request("GET", "/" + INDEX_NAME + "/_search");
            request.setJsonEntity(queryJson);

            Response response = restClient.performRequest(request);
            String responseBody = EntityUtils.toString(response.getEntity());

            // Parse the JSON response
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode hitsNode = root.path("hits").path("hits");

            if (hitsNode.isArray()) {
                for (JsonNode hit : hitsNode) {
                    JsonNode sourceNode = hit.path("_source");
                    ElasticSearchOrderDetail detail = objectMapper.treeToValue(sourceNode, ElasticSearchOrderDetail.class);
                    results.add(detail);
                }
            }
            log.info("{} returned {} results", methodName, results.size());
        } catch (IOException e) {
            log.error("Error executing {} with criteria {}: {}", methodName, criteria, e.getMessage());
        }
        return results;
    }
}
package com.example.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ElasticSearchOrderDetailsRestRepository {

    // Use the alias (or index name) that your documents are stored in.
    private static final String INDEX_NAME = "order_details_alias";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ElasticSearchOrderDetailsRestRepository(RestClient restClient) {
        this.restClient = restClient;
        this.objectMapper = new ObjectMapper();
    }

    public ElasticSearchOrderDetail findById(String id) throws IOException {
        Request request = new Request("GET", "/" + INDEX_NAME + "/_doc/" + id);
        Response response = restClient.performRequest(request);

        // If not found, you might receive a 404.
        if (response.getStatusLine().getStatusCode() == 404) {
            return null;
        }
        String responseBody = EntityUtils.toString(response.getEntity());
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode sourceNode = root.path("_source");
        if (sourceNode.isMissingNode()) {
            return null;
        }
        return objectMapper.treeToValue(sourceNode, ElasticSearchOrderDetail.class);
    }

    public List<ElasticSearchOrderDetail> findByServiceOrderId(String serviceOrderId) throws IOException {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"term\": { \"serviceOrderId\": \"" + serviceOrderId + "\" }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson);
    }

    public List<ElasticSearchOrderDetail> findByNspeId(String nspeId) throws IOException {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"term\": { \"nspeId\": \"" + nspeId + "\" }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson);
    }

    public List<ElasticSearchOrderDetail> findByPremisysQuoteId(String premisysQuoteId) throws IOException {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"term\": { \"premisysQuoteId\": \"" + premisysQuoteId + "\" }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson);
    }

    public List<ElasticSearchOrderDetail> findByOrderRequestId(String orderRequestId) throws IOException {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"term\": { \"orderRequestId\": \"" + orderRequestId + "\" }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson);
    }

    public List<ElasticSearchOrderDetail> findByTinAndOrderId(String tin, String orderId) throws IOException {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"must\": [\n" +
                "        { \"term\": { \"tin\": \"" + tin + "\" } },\n" +
                "        { \"term\": { \"orderId\": \"" + orderId + "\" } }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson);
    }

    public List<ElasticSearchOrderDetail> findByTin(String tin) throws IOException {
        String queryJson = "{\n" +
                "  \"query\": {\n" +
                "    \"term\": { \"tin\": \"" + tin + "\" }\n" +
                "  }\n" +
                "}";
        return executeSearch(queryJson);
    }

    /**
     * Executes a search query against the specified index using the given JSON query.
     *
     * @param queryJson the JSON DSL query string.
     * @return list of matching ElasticSearchOrderDetail objects.
     * @throws IOException if the request fails.
     */
    private List<ElasticSearchOrderDetail> executeSearch(String queryJson) throws IOException {
        Request request = new Request("GET", "/" + INDEX_NAME + "/_search");
        request.setJsonEntity(queryJson);

        Response response = restClient.performRequest(request);
        String responseBody = EntityUtils.toString(response.getEntity());

        // Parse the JSON response
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode hitsNode = root.path("hits").path("hits");

        List<ElasticSearchOrderDetail> results = new ArrayList<>();
        if (hitsNode.isArray()) {
            for (JsonNode hit : hitsNode) {
                JsonNode sourceNode = hit.path("_source");
                ElasticSearchOrderDetail detail = objectMapper.treeToValue(sourceNode, ElasticSearchOrderDetail.class);
                results.add(detail);
            }
        }
        return results;
    }
}





String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

private void deleteIndex(String indexName) throws IOException {
    Request deleteRequest = new Request("DELETE", "/" + indexName);
    restClient.performRequest(deleteRequest);
    log.info("Deleted old index: {}", indexName);
}


private List<String> getAllIndicesMatchingPattern(String pattern) throws IOException {
    Request request = new Request("GET", "/_cat/indices/" + pattern + "?format=json");
    Response response = restClient.performRequest(request);
    String responseBody = EntityUtils.toString(response.getEntity());

    JsonNode jsonNode = new ObjectMapper().readTree(responseBody);
    List<String> indices = new ArrayList<>();

    for (JsonNode node : jsonNode) {
        indices.add(node.get("index").asText());
    }
    return indices;
}


private String getActiveIndexForAlias(String alias) throws IOException {
    Request request = new Request("GET", "/_alias/" + alias);
    Response response = restClient.performRequest(request);
    String responseBody = EntityUtils.toString(response.getEntity());

    JsonNode jsonNode = new ObjectMapper().readTree(responseBody);
    Iterator<String> indexNames = jsonNode.fieldNames();

    if (indexNames.hasNext()) {
        return indexNames.next(); // The index currently assigned to the alias
    }
    return null;
}


public void deleteUnusedIndices(String alias) {
    try {
        String activeIndex = getActiveIndexForAlias(alias);
        List<String> allIndices = getAllIndicesMatchingPattern("my_index-*");

        for (String index : allIndices) {
            if (!index.equals(activeIndex)) {
                deleteIndex(index);
            }
        }
    } catch (Exception e) {
        log.error("Error deleting old indices: {}", e.getMessage(), e);
    }
}



GET _cat/indices/user-*?v&h=index,health,status,docs.count,store.size&format=txt

GET _cat/indices/*?v&h=index,health,status,docs.count,store.size&format=txt

GET _cat/indices?v&h=health,status,index,uuid,pri,rep,docs.count,docs.deleted,store.size,pri.store.size&s=index | grep -v '^\.'

GET _cat/indices?v&h=index&format=json


public void deleteOldIndices(String alias) {
    try {
        String activeIndex = getActiveIndexForAlias(alias);
        List<String> allIndices = getAllIndicesMatchingAlias(alias);

        for (String oldIndex : allIndices) {
            if (!oldIndex.equals(activeIndex)) { // Ensure the active index is not deleted
                Request deleteRequest = new Request("DELETE", "/" + oldIndex);
                restClient.performRequest(deleteRequest);
                log.info("Deleted old index: {}", oldIndex);
            }
        }
    } catch (Exception e) {
        log.error("Error deleting old indices: {}", e.getMessage(), e);
    }
}

private String getActiveIndexForAlias(String alias) throws IOException {
    Request request = new Request("GET", "/_alias/" + alias);
    Response response = restClient.performRequest(request);
    String responseBody = EntityUtils.toString(response.getEntity());

    JsonNode jsonNode = new ObjectMapper().readTree(responseBody);
    Iterator<String> indexNames = jsonNode.fieldNames();

    if (indexNames.hasNext()) {
        return indexNames.next(); // Active index currently assigned to the alias
    }
    return null;
}

private List<String> getAllIndicesMatchingAlias(String alias) throws IOException {
    Request request = new Request("GET", "/_cat/indices/" + alias + "*?format=json");
    Response response = restClient.performRequest(request);
    String responseBody = EntityUtils.toString(response.getEntity());

    JsonNode jsonNode = new ObjectMapper().readTree(responseBody);
    List<String> indices = new ArrayList<>();

    for (JsonNode node : jsonNode) {
        indices.add(node.get("index").asText());
    }
    return indices;
}


log.info("Switching alias {} to new index {}", alias, newIndex);
log.info("Deleting old indices for alias {}", alias);

elasticsearch:
  index:
    alias: my_index_alias

    @Value("${elasticsearch.index.alias}")
    private String indexAlias;

@Service
@Slf4j
public class ElasticsearchIndexService {

    @Autowired
    private RestClient restClient;

    public String createNewIndex() {
        String newIndexName = "my_index_" + System.currentTimeMillis();
        String requestBody = "{ \"settings\": { \"number_of_shards\": 1, \"number_of_replicas\": 1 } }";

        try {
            Request request = new Request("PUT", "/" + newIndexName);
            request.setJsonEntity(requestBody);
            Response response = restClient.performRequest(request);

            if (response.getStatusLine().getStatusCode() == 200) {
                log.info("Created new index: {}", newIndexName);
                return newIndexName;
            }
        } catch (IOException e) {
            log.error("Error creating new index: {}", newIndexName, e);
        }
        return null;
    }

    public void switchAlias(String alias, String newIndexName) {
        try {
            String requestBody = "{ \"actions\": [ { \"remove\": { \"index\": \"*\", \"alias\": \"" + alias + "\" } },"
                    + "{ \"add\": { \"index\": \"" + newIndexName + "\", \"alias\": \"" + alias + "\" } } ] }";

            Request request = new Request("POST", "/_aliases");
            request.setJsonEntity(requestBody);
            Response response = restClient.performRequest(request);

            if (response.getStatusLine().getStatusCode() == 200) {
                log.info("Alias switched to new index: {}", newIndexName);
            }
        } catch (IOException e) {
            log.error("Error switching alias to new index: {}", newIndexName, e);
        }
    }

    public void deleteOldIndices(String alias) {
        try {
            Request getAliasRequest = new Request("GET", "/_alias/" + alias);
            Response getAliasResponse = restClient.performRequest(getAliasRequest);

            String responseBody = EntityUtils.toString(getAliasResponse.getEntity());
            Map<String, Object> aliasData = new ObjectMapper().readValue(responseBody, Map.class);

            for (String oldIndex : aliasData.keySet()) {
                if (!oldIndex.equals(alias)) {
                    Request deleteRequest = new Request("DELETE", "/" + oldIndex);
                    restClient.performRequest(deleteRequest);
                    log.info("Deleted old index: {}", oldIndex);
                }
            }
        } catch (IOException e) {
            log.error("Error deleting old indices", e);
        }
    }
}



@Component
@Slf4j
public class ElasticsearchItemWriter implements ItemWriter<MyDocument> {

    @Autowired
    private RestClient restClient;

    private String newIndexName;

    public void setNewIndexName(String newIndexName) {
        this.newIndexName = newIndexName;
    }

    @Override
    public void write(List<? extends MyDocument> items) {
        if (newIndexName == null) {
            log.error("New index name is not set");
            return;
        }

        try {
            StringBuilder bulkRequest = new StringBuilder();
            for (MyDocument doc : items) {
                bulkRequest.append("{ \"index\": { \"_index\": \"").append(newIndexName).append("\" } }\n");
                bulkRequest.append(new ObjectMapper().writeValueAsString(doc)).append("\n");
            }

            Request request = new Request("POST", "/_bulk");
            request.setJsonEntity(bulkRequest.toString());
            Response response = restClient.performRequest(request);

            if (response.getStatusLine().getStatusCode() == 200) {
                log.info("Saved {} documents to index {}", items.size(), newIndexName);
            }
        } catch (IOException e) {
            log.error("Error writing documents to Elasticsearch", e);
        }
    }
}


@Configuration
@EnableBatchProcessing
@Slf4j
public class BatchJobConfig {

    @Autowired
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private ElasticsearchItemWriter elasticsearchItemWriter;

    @Autowired
    private ElasticsearchIndexService indexService;

    @Bean
    public Job elasticsearchIndexJob() {
        return jobBuilderFactory.get("elasticsearchIndexJob")
                .start(indexStep())
                .next(aliasUpdateStep())
                .next(deleteOldIndicesStep())
                .build();
    }

    @Bean
    public Step indexStep() {
        return stepBuilderFactory.get("indexStep")
                .<MyDocument, MyDocument>chunk(100)
                .reader(myItemReader())
                .processor(myItemProcessor())
                .writer(elasticsearchItemWriter)
                .listener(new StepExecutionListener() {
                    @Override
                    public void beforeStep(StepExecution stepExecution) {
                        String newIndex = indexService.createNewIndex();
                        if (newIndex != null) {
                            elasticsearchItemWriter.setNewIndexName(newIndex);
                            stepExecution.getExecutionContext().putString("newIndexName", newIndex);
                        } else {
                            throw new IllegalStateException("Failed to create new index");
                        }
                    }
                })
                .build();
    }

    @Bean
    public Step aliasUpdateStep() {
        return stepBuilderFactory.get("aliasUpdateStep")
                .tasklet((contribution, chunkContext) -> {
                    String newIndex = (String) chunkContext.getStepContext().getJobExecutionContext().get("newIndexName");
                    if (newIndex != null) {
                        indexService.switchAlias("my_index_alias", newIndex);
                    } else {
                        log.error("New index name is null, skipping alias update");
                    }
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step deleteOldIndicesStep() {
        return stepBuilderFactory.get("deleteOldIndicesStep")
                .tasklet((contribution, chunkContext) -> {
                    indexService.deleteOldIndices("my_index_alias");
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public ItemReader<MyDocument> myItemReader() {
        return new JpaPagingItemReaderBuilder<MyDocument>()
                .name("myItemReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT d FROM MyDocument d")
                .pageSize(100)
                .build();
    }

    @Bean
    public ItemProcessor<MyDocument, MyDocument> myItemProcessor() {
        return item -> {
            log.info("Processing document: {}", item.getId());
            return item;
        };
    }
}
