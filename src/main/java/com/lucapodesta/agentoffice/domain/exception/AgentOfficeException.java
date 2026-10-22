package com.lucapodesta.agentoffice.domain.exception;

public abstract class AgentOfficeException extends RuntimeException {

    private final ErrorCode code;

    protected AgentOfficeException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
