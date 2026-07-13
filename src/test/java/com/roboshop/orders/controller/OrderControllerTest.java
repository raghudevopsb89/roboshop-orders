package com.roboshop.orders.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roboshop.orders.model.Order;
import com.roboshop.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice tests for {@link OrderController}. The MongoRepository is mocked with
 * {@link MockBean} so no real Mongo / RabbitMQ infrastructure is required.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderRepository orderRepository;

    private Order sampleOrder() {
        Order order = new Order();
        order.setId("o1");
        order.setUserId("u1");
        order.setUserEmail("alice@example.com");
        order.setUserName("Alice");
        order.setTotal(99.5);
        order.setStatus("CONFIRMED");
        return order;
    }

    @Test
    void health_returnsOkJson() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.service").value("orders"));
    }

    @Test
    void getOrder_whenFound_returns200AndBody() throws Exception {
        when(orderRepository.findById("o1")).thenReturn(Optional.of(sampleOrder()));

        mockMvc.perform(get("/orders/o1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("o1"))
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.total").value(99.5));
    }

    @Test
    void getOrder_whenMissing_returns404() throws Exception {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/orders/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserOrders_returnsList() throws Exception {
        when(orderRepository.findByUserIdOrderByOrderDateDesc("u1"))
                .thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/orders/user/u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("o1"))
                .andExpect(jsonPath("$[0].userId").value("u1"));
    }

    @Test
    void createOrder_savesAndReturnsOrder() throws Exception {
        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder());

        Order request = new Order();
        request.setUserId("u1");
        request.setTotal(99.5);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("o1"))
                .andExpect(jsonPath("$.userId").value("u1"));

        verify(orderRepository).save(any(Order.class));
    }
}
