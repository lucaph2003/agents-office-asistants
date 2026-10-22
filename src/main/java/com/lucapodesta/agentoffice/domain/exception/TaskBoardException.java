package com.lucapodesta.agentoffice.domain.exception;

public class TaskBoardException extends AgentOfficeException {

    public TaskBoardException(String message, Throwable cause) {
        super(ErrorCode.TRELLO_UNAVAILABLE, message, cause);
    }
}
