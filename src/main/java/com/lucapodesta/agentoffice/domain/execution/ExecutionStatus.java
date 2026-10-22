package com.lucapodesta.agentoffice.domain.execution;

public enum ExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    /** Finished successfully but its output was not accepted by review. */
    REJECTED
}
