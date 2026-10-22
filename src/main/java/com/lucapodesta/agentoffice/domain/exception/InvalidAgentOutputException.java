package com.lucapodesta.agentoffice.domain.exception;

/** The model answered, but not in a shape the agent can use. */
public class InvalidAgentOutputException extends AgentOfficeException {

    public InvalidAgentOutputException(String message) {
        super(ErrorCode.INVALID_AGENT_OUTPUT, message);
    }
}
