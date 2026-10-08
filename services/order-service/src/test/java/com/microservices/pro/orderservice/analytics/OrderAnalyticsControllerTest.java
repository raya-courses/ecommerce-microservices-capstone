package com.microservices.pro.orderservice.analytics;

import com.microservices.pro.orderservice.analytics.dto.AnalyticsSummaryResponse;
import com.microservices.pro.orderservice.analytics.dto.HourlyOrdersDto;
import com.microservices.pro.orderservice.analytics.dto.HourlyRevenueDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderAnalyticsController.class)
class OrderAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderAnalyticsService orderAnalyticsService;

    @Test
    void getSummary_returns200Ok_withExpectedAnalyticsJsonStructure() throws Exception {
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                120L,
                95L,
                15L,
                10L,
                0.125,
                new BigDecimal("18450.00"),
                Map.of("CONFIRMED", 95L, "CANCELLED", 15L, "PENDING", 10L),
                List.of(new HourlyOrdersDto("2026-09-29T10:00:00Z", 25, Map.of("CONFIRMED", 20L, "CANCELLED", 3L, "PENDING", 2L))),
                List.of(new HourlyRevenueDto("2026-09-29T10:00:00Z", new BigDecimal("3500.00")))
        );

        when(orderAnalyticsService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalOrders").value(120))
                .andExpect(jsonPath("$.confirmedOrders").value(95))
                .andExpect(jsonPath("$.cancelledOrders").value(15))
                .andExpect(jsonPath("$.pendingOrders").value(10))
                .andExpect(jsonPath("$.cancelledRatio").value(0.125))
                .andExpect(jsonPath("$.totalRevenue").value(18450.00))
                .andExpect(jsonPath("$.ordersByStatus.CONFIRMED").value(95))
                .andExpect(jsonPath("$.ordersByStatus.CANCELLED").value(15))
                .andExpect(jsonPath("$.ordersByStatus.PENDING").value(10))
                .andExpect(jsonPath("$.ordersPerHour[0].hour").value("2026-09-29T10:00:00Z"))
                .andExpect(jsonPath("$.ordersPerHour[0].count").value(25))
                .andExpect(jsonPath("$.revenuePerHour[0].revenue").value(3500.00));
    }
}
