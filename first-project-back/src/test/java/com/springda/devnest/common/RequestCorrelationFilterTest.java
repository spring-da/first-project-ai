package com.springda.devnest.common;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void preservesSafeCallerIdAcrossHeaderRequestAndLogContext() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/tasks");
        request.addHeader(RequestCorrelationFilter.HEADER, "web-incident_42");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(RequestCorrelationFilter.requestId(request)).isEqualTo("web-incident_42");
            assertThat(MDC.get("requestId")).isEqualTo("web-incident_42");
        });

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo("web-incident_42");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesUnsafeCallerIdWithServerGeneratedUuid() throws Exception {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader(RequestCorrelationFilter.HEADER, "contains spaces and log controls");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(response.getHeader(RequestCorrelationFilter.HEADER))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void unexpectedFailuresReturnGenericDetailAndRequestReference() {
        var request = new MockHttpServletRequest("GET", "/api/v1/tasks");
        request.setAttribute(RequestCorrelationFilter.ATTRIBUTE, "server-failure-7");

        var detail = new GlobalExceptionHandler().handleUnexpected(
                new IllegalStateException("database-password-must-not-leak"), request);

        assertThat(detail.getStatus()).isEqualTo(500);
        assertThat(detail.getDetail()).isEqualTo("服务器暂时无法处理请求，请稍后重试。");
        assertThat(detail.getDetail()).doesNotContain("database-password-must-not-leak");
        assertThat(detail.getProperties()).containsEntry("requestId", "server-failure-7");
    }
}
