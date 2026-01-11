package org.fintech.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.Reason;
import org.fintech.proto.v1.RiskAssessment;
import org.fintech.proto.v1.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RUN_LOCALSTACK_TESTS", matches = "true")
class SqsOutboxIntegrationTest {

    private static final DockerImageName LOCALSTACK_IMAGE =
        DockerImageName.parse("localstack/localstack:3.5");
    @Container
    private static final LocalStackContainer localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
        .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.DYNAMODB)
        .withStartupTimeout(Duration.ofMinutes(3));

    private static final String TRANSACTIONS_QUEUE = "fintech-transactions";
    private static final String DECISIONS_QUEUE = "fintech-decisions";
    private static final String OUTBOX_TABLE = "fintech-outbox";

    private static String transactionsQueueUrl;
    private static String decisionsQueueUrl;

    @Autowired
    private SqsClient sqsClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        ensureLocalStackResources();

        registry.add("sqs.enabled", () -> true);
        registry.add("sqs.queue-url", () -> transactionsQueueUrl);
        registry.add("sqs.region", () -> localstack.getRegion());
        registry.add("sqs.endpoint-override", () -> localstack.getEndpointOverride(LocalStackContainer.Service.SQS)
            .toString());
        registry.add("sqs.access-key", () -> localstack.getAccessKey());
        registry.add("sqs.secret-key", () -> localstack.getSecretKey());

        registry.add("outbox.enabled", () -> true);
        registry.add("outbox.table-name", () -> OUTBOX_TABLE);
        registry.add("outbox.decision-queue-url", () -> decisionsQueueUrl);
        registry.add("outbox.region", () -> localstack.getRegion());
        registry.add("outbox.endpoint-override", () -> localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB)
            .toString());
        registry.add("outbox.access-key", () -> localstack.getAccessKey());
        registry.add("outbox.secret-key", () -> localstack.getSecretKey());
        registry.add("outbox.poll-interval-millis", () -> 250L);
    }

    @Test
    void publishesDecisionFromOutbox() throws Exception {
        TransactionRequest request = TransactionRequest.newBuilder()
            .setTransactionId("tx-outbox-1")
            .setAccountId("acct-9")
            .setAmount(15000.0)
            .setMerchant("ACME")
            .setCurrency("USD")
            .setTimestamp("2026-01-06T16:06:34+08:00")
            .build();

        sqsClient.sendMessage(SendMessageRequest.builder()
            .queueUrl(transactionsQueueUrl)
            .messageBody(Base64.getEncoder().encodeToString(request.toByteArray()))
            .build());

        RiskAssessment assessment = awaitDecisionMessage();

        assertThat(assessment.getTransactionId()).isEqualTo("tx-outbox-1");
        assertThat(assessment.getDecision()).isEqualTo(Decision.REJECT);
        assertThat(assessment.getReason()).isEqualTo(Reason.AMOUNT_EXCEEDS_HARD_LIMIT);
        assertThat(assessment.getRiskScore()).isEqualTo(0.95);
    }

    private static void ensureLocalStackResources() {
        if (transactionsQueueUrl != null) {
            return;
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
            localstack.getAccessKey(),
            localstack.getSecretKey()
        );
        StaticCredentialsProvider provider = StaticCredentialsProvider.create(credentials);

        try (SqsClient sqs = SqsClient.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(provider)
            .build()) {
            transactionsQueueUrl = sqs.createQueue(CreateQueueRequest.builder()
                .queueName(TRANSACTIONS_QUEUE)
                .build()).queueUrl();
            decisionsQueueUrl = sqs.createQueue(CreateQueueRequest.builder()
                .queueName(DECISIONS_QUEUE)
                .build()).queueUrl();
        }

        try (DynamoDbClient dynamoDb = DynamoDbClient.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
            .region(Region.of(localstack.getRegion()))
            .credentialsProvider(provider)
            .build()) {
            CreateTableRequest request = CreateTableRequest.builder()
                .tableName(OUTBOX_TABLE)
                .attributeDefinitions(
                    AttributeDefinition.builder().attributeName("outbox_id").attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder().attributeName("status").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("created_at").attributeType(ScalarAttributeType.N)
                        .build()
                )
                .keySchema(KeySchemaElement.builder().attributeName("outbox_id").keyType(KeyType.HASH).build())
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                    .indexName("status-index")
                    .keySchema(
                        KeySchemaElement.builder().attributeName("status").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("created_at").keyType(KeyType.RANGE).build()
                    )
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L)
                        .writeCapacityUnits(5L).build())
                    .build())
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L)
                    .writeCapacityUnits(5L).build())
                .build();
            dynamoDb.createTable(request);
        } catch (ResourceInUseException ignored) {
            // Table already exists.
        }
    }

    private RiskAssessment awaitDecisionMessage() throws Exception {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        while (System.currentTimeMillis() < deadline) {
            for (Message message : sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(decisionsQueueUrl)
                .waitTimeSeconds(1)
                .maxNumberOfMessages(1)
                .build()).messages()) {
                byte[] payload = Base64.getDecoder().decode(message.body());
                return RiskAssessment.parseFrom(payload);
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Timed out waiting for outbox decision message");
    }
}
