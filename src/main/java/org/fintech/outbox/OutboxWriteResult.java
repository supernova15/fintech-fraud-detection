package org.fintech.outbox;

public record OutboxWriteResult(String outboxId, boolean created) {}
