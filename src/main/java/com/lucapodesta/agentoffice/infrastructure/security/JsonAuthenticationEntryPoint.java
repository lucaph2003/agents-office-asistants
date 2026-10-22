package com.lucapodesta.agentoffice.infrastructure.security;

import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.infrastructure.observability.CorrelationIdFilter;
import com.lucapodesta.agentoffice.infrastructure.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

/** Answers unauthenticated API calls with the same error body as every other failure. */
class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JsonAuthenticationEntryPoint.class);

    private final JsonMapper jsonMapper;
    private final Clock clock;

    JsonAuthenticationEntryPoint(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        log.warn("event=REQUEST_REJECTED code={} method={} path={}", ErrorCode.UNAUTHORIZED, request.getMethod(),
                request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), new ErrorResponse(ErrorCode.UNAUTHORIZED.name(),
                "Missing or invalid " + ApiKeyAuthenticationFilter.HEADER + " header",
                MDC.get(CorrelationIdFilter.MDC_KEY), clock.instant(), List.of()));
    }
}
