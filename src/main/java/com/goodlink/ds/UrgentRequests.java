package com.goodlink.ds;

import com.goodlink.model.UrgentRequest;
import java.util.function.Consumer;

/**
 * Custom dual-queue data structure for urgent vs normal requests.
 * Internally uses two hand-written MyQueue instances.
 * Invariant: urgent queue is ALWAYS served first before normal queue.
 */
public class UrgentRequests {

    private final MyQueue<UrgentRequest> urgentQueue;
    private final MyQueue<UrgentRequest> normalQueue;

    public UrgentRequests() {
        this.urgentQueue = new MyQueue<>();
        this.normalQueue = new MyQueue<>();
    }

    /**
     * Enqueues request into urgentQueue or normalQueue based on request.isUrgent().
     */
    public synchronized void enqueue(UrgentRequest request) {
        if (request == null) return;
        if (request.isUrgent()) {
            urgentQueue.enqueue(request);
        } else {
            normalQueue.enqueue(request);
        }
    }

    /**
     * Dequeues the next highest-priority request.
     * Urgent requests are ALWAYS served before normal requests.
     */
    public synchronized UrgentRequest dequeue() {
        if (!urgentQueue.isEmpty()) {
            return urgentQueue.dequeue();
        }
        return normalQueue.dequeue();
    }

    /**
     * Peeks at the next request to be served without removing it.
     */
    public synchronized UrgentRequest peek() {
        if (!urgentQueue.isEmpty()) {
            return urgentQueue.peek();
        }
        return normalQueue.peek();
    }

    public synchronized int size() {
        return urgentQueue.size() + normalQueue.size();
    }

    public synchronized int getUrgentCount() {
        return urgentQueue.size();
    }

    public synchronized int getNormalCount() {
        return normalQueue.size();
    }

    public synchronized boolean isEmpty() {
        return urgentQueue.isEmpty() && normalQueue.isEmpty();
    }

    /**
     * Iterates through urgent requests first, then normal requests.
     */
    public synchronized void forEach(Consumer<UrgentRequest> action) {
        urgentQueue.forEach(action);
        normalQueue.forEach(action);
    }

    /**
     * Searches for a request by its unique numeric requestId.
     */
    public synchronized UrgentRequest findById(int requestId) {
        final UrgentRequest[] match = {null};
        forEach(req -> {
            if (match[0] == null && req.getRequestId() == requestId) {
                match[0] = req;
            }
        });
        return match[0];
    }
}
