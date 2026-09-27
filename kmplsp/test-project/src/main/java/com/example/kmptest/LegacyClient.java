package com.example.kmptest;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-Java collaborator, so the sample covers Java support and Kotlin/Java interop.
 *
 * <p>It calls back into the Kotlin {@code LegacyBridge} object: navigating from
 * {@code LegacyBridge#describe} here, or from the Kotlin call site in {@code Main.kt} into
 * {@link #send}, exercises both directions of the resolver.
 */
public final class LegacyClient {

    private final String endpoint;
    private final List<String> sent = new ArrayList<>();

    public LegacyClient(String endpoint) {
        this.endpoint = endpoint;
    }

    /** Sends one task and records what went over the wire. */
    public void send(Task task, boolean urgent) {
        String line = LegacyBridge.describe(task, urgent ? "!" : ">");
        sent.add(line);
        System.out.println(endpoint + " <- " + line);
    }

    /** Sends every task in the list, skipping the completed ones. */
    public int sendAll(List<Task> tasks) {
        int sentCount = 0;
        for (Task task : tasks) {
            if (task.getDone()) {
                continue;
            }
            send(task, task.getPriority() == Priority.URGENT);
            sentCount++;
        }
        return sentCount;
    }

    public List<String> getSent() {
        return sent;
    }

    public int getRetryBudget() {
        return (int) LegacyBridge.retry(2, 50);
    }
}
