package com.lucapodesta.agentoffice.domain.exception;

public class LanguageModelException extends AgentOfficeException {

    public LanguageModelException(ErrorCode code, String message) {
        super(code, message);
    }

    public LanguageModelException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
