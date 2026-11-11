package com.goodlink.ds;

import java.util.function.Consumer;

/**
 * Custom generic FIFO queue.
 * Supported operations: enqueue, dequeue, peek, size, isEmpty, forEach.
 * Used for:
 * - Donations queue (FIFO order)
 * - Internal queues inside UrgentRequests
 */
public class MyQueue<T> {

    public static class QueueNode<T> {
        public T data;
        public QueueNode<T> next;

        public QueueNode(T data) {
            this.data = data;
            this.next = null;
        }
    }

    private QueueNode<T> front;
    private QueueNode<T> rear;
    private int size;

    public MyQueue() {
        this.front = null;
        this.rear = null;
        this.size = 0;
    }

    /**
     * Enqueue: Adds an element to the rear of the queue.
     */
    public synchronized void enqueue(T item) {
        QueueNode<T> newNode = new QueueNode<>(item);
        if (rear == null) {
            front = newNode;
            rear = newNode;
        } else {
            rear.next = newNode;
            rear = newNode;
        }
        size++;
    }

    /**
     * Dequeue: Removes and returns the front element, or null if empty.
     */
    public synchronized T dequeue() {
        if (isEmpty()) {
            return null;
        }
        T data = front.data;
        front = front.next;
        if (front == null) {
            rear = null;
        }
        size--;
        return data;
    }

    /**
     * Peek: Returns the front element without removing it.
     */
    public synchronized T peek() {
        if (isEmpty()) {
            return null;
        }
        return front.data;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public synchronized void forEach(Consumer<T> action) {
        QueueNode<T> curr = front;
        while (curr != null) {
            action.accept(curr.data);
            curr = curr.next;
        }
    }
}
