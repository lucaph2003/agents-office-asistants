package com.lucapodesta.agentoffice.infrastructure.ollama;

import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.exception.LanguageModelException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin HTTP client for the Ollama chat API. Translates transport problems into
 * {@link LanguageModelException} and retries only failures that a retry can fix.
 */
class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final RestClient restClient;
    private final int maxAttempts;
    private final Duration retryBackoff;

    OllamaClient(RestClient restClient, int maxAttempts, Duration retryBackoff) {
        this.restClient = restClient;
        this.maxAttempts = maxAttempts;
        this.retryBackoff = retryBackoff;
    }

    OllamaChatResponse chat(OllamaChatRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return restClient.post()
                        .uri("/api/chat")
                        .body(request)
                        .retrieve()
                        .body(OllamaChatResponse.class);
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                if (isTimeout(ex)) {
                    throw new LanguageModelException(ErrorCode.OLLAMA_UNAVAILABLE,
                            "Ollama did not answer in time (model " + request.model() + ")", ex);
                }
                if (attempt >= maxAttempts) {
                    throw new LanguageModelException(ErrorCode.OLLAMA_UNAVAILABLE,
                            "Ollama is unavailable after " + attempt + " attempt(s): " + describe(ex), ex);
                }
                log.warn("event=LLM_CALL_RETRY attempt={} model={} reason=\"{}\"", attempt, request.model(), describe(ex));
                pause();
            } catch (HttpClientErrorException ex) {
                throw clientError(request.model(), ex);
            } catch (RestClientException ex) {
                throw new LanguageModelException(ErrorCode.OLLAMA_UNAVAILABLE,
                        "Ollama returned a response that could not be read", ex);
            }
        }
    }

    private static LanguageModelException clientError(String model, HttpClientErrorException ex) {
        if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return new LanguageModelException(ErrorCode.AGENT_NOT_AVAILABLE,
                    "Model '%s' is not available in Ollama (try: ollama pull %s)".formatted(model, model), ex);
        }
        return new LanguageModelException(ErrorCode.OLLAMA_UNAVAILABLE,
                "Ollama rejected the request with status " + ex.getStatusCode().value(), ex);
    }

    private static boolean isTimeout(Exception ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Exception ex) {
        return ex instanceof HttpServerErrorException server
                ? "status " + server.getStatusCode().value()
                : ex.getClass().getSimpleName();
    }

    private void pause() {
        try {
            Thread.sleep(retryBackoff);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LanguageModelException(ErrorCode.OLLAMA_UNAVAILABLE, "Interrupted while waiting to retry", ex);
        }
    }
}
