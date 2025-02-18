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
