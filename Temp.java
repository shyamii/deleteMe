import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchConfig {
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConfig.class);

    private String aliasName;
    private String currentIndexName;

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        logger.info("Setting aliasName to: {}", aliasName);
        this.aliasName = aliasName;
    }

    public String getCurrentIndexName() {
        return currentIndexName;
    }

    public void setCurrentIndexName(String currentIndexName) {
        logger.info("Setting currentIndexName to: {}", currentIndexName);
        this.currentIndexName = currentIndexName;
    }
}



import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

@Service
public class ElasticsearchService {
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchService.class);

    private final RestClient restClient;

    public ElasticsearchService(RestClient restClient) {
        this.restClient = restClient;
    }

    public String readIndexDefinition(String filePath) throws IOException {
        logger.info("Reading index definition from file: {}", filePath);
        ClassPathResource resource = new ClassPathResource(filePath);
        byte[] bytes = Files.readAllBytes(Paths.get(resource.getURI()));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public boolean aliasExists(String aliasName) throws IOException {
        logger.info("Checking if alias exists: {}", aliasName);
        Request request = new Request("HEAD", "/_alias/" + aliasName);
        Response response = restClient.performRequest(request);
        return response.getStatusLine().getStatusCode() == 200;
    }

    public String getCurrentIndexName(String aliasName) throws IOException {
        logger.info("Getting current index name for alias: {}", aliasName);
        Request request = new Request("GET", "/_alias/" + aliasName);
        Response response = restClient.performRequest(request);
        Map<String, Object> responseMap = JsonUtils.parseResponse(response);
        Set<String> indices = responseMap.keySet();
        return indices.stream().findFirst().orElse(null);
    }

    public void createIndex(String indexName, String indexDefinition) throws IOException {
        logger.info("Creating index: {}", indexName);
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(indexDefinition);
        restClient.performRequest(request);
    }

    public void switchAlias(String aliasName, String oldIndexName, String newIndexName) throws IOException {
        logger.info("Switching alias: {} from {} to {}", aliasName, oldIndexName, newIndexName);
        String jsonBody = String.format(
            "{\"actions\": [" +
                "{\"remove\": {\"index\": \"%s\", \"alias\": \"%s\"}}," +
                "{\"add\": {\"index\": \"%s\", \"alias\": \"%s\"}}" +
            "]}",
            oldIndexName, aliasName, newIndexName, aliasName
        );

        Request request = new Request("POST", "/_aliases");
        request.setJsonEntity(jsonBody);
        restClient.performRequest(request);
    }

    public void deleteIndex(String indexName) throws IOException {
        logger.info("Deleting index: {}", indexName);
        Request request = new Request("DELETE", "/" + indexName);
        restClient.performRequest(request);
    }
}

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class JsonUtils {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static Map<String, Object> parseResponse(Response response) throws IOException {
        logger.info("Parsing Elasticsearch response");
        return mapper.readValue(response.getEntity().getContent(), Map.class);
    }
}


        import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
public class ElasticsearchWriter {
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchWriter.class);

    private final ElasticsearchClient client;
    private final ElasticsearchConfig config;

    @Autowired
    public ElasticsearchWriter(ElasticsearchClient client, ElasticsearchConfig config) {
        this.client = client;
        this.config = config;
    }

    public void writeData(String documentId, String jsonDocument) throws IOException {
        logger.info("Writing document with ID: {} to index: {}", documentId, config.getCurrentIndexName());
        IndexRequest<String> request = IndexRequest.of(b -> b
            .index(config.getCurrentIndexName())
            .id(documentId)
            .document(jsonDocument)
        );

        IndexResponse response = client.index(request);
        logger.info("Indexed document with ID: {}", response.id());
    }
}


import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableBatchProcessing
public class BatchConfig {
    private static final Logger logger = LoggerFactory.getLogger(BatchConfig.class);

    @Autowired
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private ElasticsearchConfig elasticsearchConfig;

    @Bean
    public Job dataMigrationJob() {
        return jobBuilderFactory.get("dataMigrationJob")
                .incrementer(new RunIdIncrementer())
                .start(initializeAliasStep())
                .next(createNewIndexStep())
                .next(loadDataStep())
                .next(switchAliasStep())
                .next(deleteOldIndexStep())
                .build();
    }

    @Bean
    public Step initializeAliasStep() {
        return stepBuilderFactory.get("initializeAliasStep")
                .tasklet((contribution, chunkContext) -> {
                    logger.info("Initializing alias");
                    if (!elasticsearchService.aliasExists(elasticsearchConfig.getAliasName())) {
                        String indexDefinition = elasticsearchService.readIndexDefinition("index.json");
                        String initialIndexName = "my_index_v1";
                        elasticsearchService.createIndex(initialIndexName, indexDefinition);
                        elasticsearchService.switchAlias(elasticsearchConfig.getAliasName(), null, initialIndexName);
                        elasticsearchConfig.setCurrentIndexName(initialIndexName);
                    }
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step createNewIndexStep() {
        return stepBuilderFactory.get("createNewIndexStep")
                .tasklet((contribution, chunkContext) -> {
                    logger.info("Creating new index");
                    String currentIndexName = elasticsearchConfig.getCurrentIndexName();
                    int currentVersion = Integer.parseInt(currentIndexName.split("_v")[1]);
                    String newIndexName = "my_index_v" + (currentVersion + 1);

                    String indexDefinition = elasticsearchService.readIndexDefinition("index.json");
                    elasticsearchService.createIndex(newIndexName, indexDefinition);
                    elasticsearchConfig.setCurrentIndexName(newIndexName);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step loadDataStep(ElasticsearchWriter writer) {
        return stepBuilderFactory.get("loadDataStep")
                .tasklet((contribution, chunkContext) -> {
                    logger.info("Loading data into new index");
                    writer.writeData("1", "{\"field1\": \"value1\", \"field2\": \"value2\"}");
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step switchAliasStep() {
        return stepBuilderFactory.get("switchAliasStep")
                .tasklet((contribution, chunkContext) -> {
                    logger.info("Switching alias to new index");
                    String currentIndexName = elasticsearchConfig.getCurrentIndexName();
                    String newIndexName = "my_index_v" + (Integer.parseInt(currentIndexName.split("_v")[1]) + 1);

                    elasticsearchService.switchAlias(elasticsearchConfig.getAliasName(), currentIndexName, newIndexName);
                    elasticsearchConfig.setCurrentIndexName(newIndexName);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step deleteOldIndexStep() {
        return stepBuilderFactory.get("deleteOldIndexStep")
                .tasklet((contribution, chunkContext) -> {
                    logger.info("Deleting old index");
                    String currentIndexName = elasticsearchConfig.getCurrentIndexName();
                    String oldIndexName = "my_index_v" + (Integer.parseInt(currentIndexName.split("_v")[1]) - 1);

                    elasticsearchService.deleteIndex(oldIndexName);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }
}
            
