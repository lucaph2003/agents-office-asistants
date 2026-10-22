package com.lucapodesta.agentoffice.domain.exception;

public class NotificationException extends AgentOfficeException {

    public NotificationException(String message, Throwable cause) {
        super(ErrorCode.TELEGRAM_NOTIFICATION_FAILED, message, cause);
    }
}
