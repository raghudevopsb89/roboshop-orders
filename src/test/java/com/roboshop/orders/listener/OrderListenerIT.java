package com.roboshop.orders.listener;

import com.roboshop.orders.config.RabbitConfig;
import com.roboshop.orders.model.Order;
import com.roboshop.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Component integration test for the Orders service message pipeline.
 *
 * <p>Boots the full Spring context against a real MongoDB and a real RabbitMQ, both provided
 * by Testcontainers and wired into the context via {@link ServiceConnection} (Spring Boot 3.1+).
 * This exercises the real broker topology declared in {@link RabbitConfig} (the {@code roboshop}
 * DirectExchange, the durable {@code orders} queue and their binding), the real
 * {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter} on both send
 * and receive, the real {@link OrderListener} deserialization of the event {@code Map}, and the
 * real persistence of an {@link Order} into MongoDB.
 *
 * <p>Cross-service HTTP is the only thing mocked. {@link OrderListener} creates its
 * {@link RestTemplate} internally with {@code new RestTemplate()} (it is NOT a Spring bean),
 * so {@code @MockBean RestTemplate} would have no effect. Mirroring the existing unit test, we
 * inject a Mockito mock into the autowired listener bean via {@link ReflectionTestUtils}; the
 * mock returns a canned shipping response and a no-op notification. This keeps the mock
 * component-scoped while leaving Rabbit + Mongo fully real.
 *
 * <p>These tests were NOT executed locally (no JRE on the authoring machine). They are written
 * to compile and run under CI (self-hosted runner with Docker + JDK 17) via {@code mvn verify}.
 */
@SpringBootTest
@Testcontainers
class OrderListenerIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderListener orderListener;

    private final RestTemplate mockRestTemplate = mock(RestTemplate.class);

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        // Canned shipping enrichment; notification post is a no-op (returns null).
        when(mockRestTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("shippingCost", 12.5, "city", "New York"));
        when(mockRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("sent");

        // OrderListener builds its RestTemplate with `new` (not injected), so replace the field
        // on the real, container-managed bean before any message is consumed.
        ReflectionTestUtils.setField(orderListener, "restTemplate", mockRestTemplate);
    }

    @Test
    void handleOrderEvent_publishedToExchange_isConsumedAndPersisted() {
        Map<String, Object> item = Map.of(
                "productId", 101,
                "name", "Robot Arm",
                "sku", "RA-101",
                "price", 49.99,
                "quantity", 2
        );

        Map<String, Object> event = Map.of(
                "userId", "u1",
                "userEmail", "alice@example.com",
                "userName", "Alice",
                "total", 99.98,
                "transactionId", "txn-123",
                "cityId", 1,
                "items", List.of(item)
        );

        // Publish through the real exchange + routing key; the configured Jackson converter
        // serializes the Map to JSON and the listener deserializes it back.
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(orderRepository.count()).isEqualTo(1L));

        List<Order> orders = orderRepository.findAll();
        assertThat(orders).hasSize(1);

        Order saved = orders.get(0);
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getUserEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getUserName()).isEqualTo("Alice");
        assertThat(saved.getTotal()).isEqualTo(99.98);
        assertThat(saved.getTransactionId()).isEqualTo("txn-123");
        assertThat(saved.getStatus()).isEqualTo("CONFIRMED");

        // Shipping enrichment from the mocked cross-service call.
        assertThat(saved.getShippingCost()).isEqualTo(12.5);
        assertThat(saved.getShippingCity()).isEqualTo("New York");

        assertThat(saved.getItems()).hasSize(1);
        Order.OrderItem savedItem = saved.getItems().get(0);
        assertThat(savedItem.getProductId()).isEqualTo(101);
        assertThat(savedItem.getName()).isEqualTo("Robot Arm");
        assertThat(savedItem.getSku()).isEqualTo("RA-101");
        assertThat(savedItem.getPrice()).isEqualTo(49.99);
        assertThat(savedItem.getQuantity()).isEqualTo(2);
    }
}
