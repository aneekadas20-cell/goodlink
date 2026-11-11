package com.goodlink.ds;

import java.util.function.Consumer;

/**
 * Custom generic singly/doubly linked list.
 * Supported operations: add, addFirst, remove, contains, forEach, size.
 * Used for:
 * - Volunteers per activity
 * - Achievements wall (newest first via addFirst)
 */
public class MyLinkedList<T> {

    public static class Node<T> {
        public T data;
        public Node<T> next;

        public Node(T data) {
            this.data = data;
            this.next = null;
        }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;

    public MyLinkedList() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    /**
     * Appends an element to the end of the list.
     */
    public synchronized void add(T item) {
        Node<T> newNode = new Node<>(item);
        if (head == null) {
            head = newNode;
            tail = newNode;
        } else {
            tail.next = newNode;
            tail = newNode;
        }
        size++;
    }

    /**
     * Prepends an element to the front of the list (newest first).
     */
    public synchronized void addFirst(T item) {
        Node<T> newNode = new Node<>(item);
        if (head == null) {
            head = newNode;
            tail = newNode;
        } else {
            newNode.next = head;
            head = newNode;
        }
        size++;
    }

    /**
     * Removes the first occurrence of the specified element.
     */
    public synchronized boolean remove(T item) {
        if (head == null) {
            return false;
        }
        if (head.data != null && head.data.equals(item)) {
            head = head.next;
            if (head == null) {
                tail = null;
            }
            size--;
            return true;
        }
        Node<T> current = head;
        while (current.next != null) {
            if (current.next.data != null && current.next.data.equals(item)) {
                if (current.next == tail) {
                    tail = current;
                }
                current.next = current.next.next;
                size--;
                return true;
            }
            current = current.next;
        }
        return false;
    }

    /**
     * Checks if the list contains the given element.
     */
    public synchronized boolean contains(T item) {
        Node<T> current = head;
        while (current != null) {
            if (current.data != null && current.data.equals(item)) {
                return true;
            }
            current = current.next;
        }
        return false;
    }

    /**
     * Traverses the list and executes the consumer action on each element.
     */
    public synchronized void forEach(Consumer<T> action) {
        Node<T> current = head;
        while (current != null) {
            action.accept(current.data);
            current = current.next;
        }
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public synchronized T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size " + size);
        }
        Node<T> current = head;
        for (int i = 0; i < index; i++) {
            current = current.next;
        }
        return current.data;
    }

    public Node<T> getHead() {
        return head;
    }
}
