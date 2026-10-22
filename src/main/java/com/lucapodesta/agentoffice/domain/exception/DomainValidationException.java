package com.lucapodesta.agentoffice.domain.exception;

public class DomainValidationException extends AgentOfficeException {

    public DomainValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
