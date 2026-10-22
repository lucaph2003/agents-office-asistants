package com.lucapodesta.agentoffice.application.port.out;

/**
 * Tells the outside world that a task reached a point a human cares about. Which channel
 * (Telegram today) is decided on the other side, not here.
 *
 * @throws com.lucapodesta.agentoffice.domain.exception.NotificationException when delivery fails
 */
public interface NotificationPort {

    void notify(TaskNotification notification);
}
