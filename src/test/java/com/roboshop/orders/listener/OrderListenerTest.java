package com.roboshop.orders.listener;

import com.roboshop.orders.model.Order;
import com.roboshop.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain Mockito unit test for the RabbitMQ consumer {@link OrderListener}.
 * The internally-created {@link RestTemplate} is replaced with a mock via
 * {@link ReflectionTestUtils} so the test is fully hermetic (no network calls).
 */
@ExtendWith(MockitoExtension.class)
class OrderListenerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestTemplate restTemplate;

    private OrderListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderListener(orderRepository);
        // Override the production-instantiated RestTemplate and the @Value fields.
        ReflectionTestUtils.setField(listener, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(listener, "shippingUrl", "http://shipping:8004");
        ReflectionTestUtils.setField(listener, "notificationUrl", "http://notification:8008");
    }

    @Test
    void handleOrderEvent_savesOrderWithMappedFields() {
        // Shipping service response returned by the mocked RestTemplate.
        Map<String, Object> shippingResp = Map.of("shippingCost", 12.5, "city", "New York");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(shippingResp);

        // repository.save assigns an id and echoes the entity back.
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(Order.class)))
                .thenAnswer(invocation -> {
                    Order arg = invocation.getArgument(0);
                    arg.setId("generated-id");
                    return arg;
                });

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

        listener.handleOrderEvent(event);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).save(captor.capture());

        Order saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getUserEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getUserName()).isEqualTo("Alice");
        assertThat(saved.getTotal()).isEqualTo(99.98);
        assertThat(saved.getTransactionId()).isEqualTo("txn-123");
        assertThat(saved.getStatus()).isEqualTo("CONFIRMED");
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

    @Test
    void handleOrderEvent_withoutCityId_savesWithZeroShippingCost() {
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(Order.class)))
                .thenAnswer(invocation -> {
                    Order arg = invocation.getArgument(0);
                    arg.setId("generated-id");
                    return arg;
                });

        Map<String, Object> event = Map.of(
                "userId", "u2",
                "total", 10.0,
                "transactionId", "txn-456"
        );

        listener.handleOrderEvent(event);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());

        Order saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("u2");
        assertThat(saved.getStatus()).isEqualTo("CONFIRMED");
        assertThat(saved.getShippingCost()).isEqualTo(0.0);
        assertThat(saved.getShippingCity()).isEqualTo("");
        assertThat(saved.getUserEmail()).isEqualTo("");
        assertThat(saved.getUserName()).isEqualTo("");
    }
}
