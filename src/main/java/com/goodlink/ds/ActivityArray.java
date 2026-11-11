package com.goodlink.ds;

import com.goodlink.model.Activity;
import java.util.function.Consumer;

/**
 * Hand-written array data structure for the list of activities.
 * Backed by a primitive Activity[] array with dynamic growth capability.
 * Does NOT use java.util collection classes.
 */
public class ActivityArray {
    private Activity[] elements;
    private int size;

    public ActivityArray() {
        this(10);
    }

    public ActivityArray(int initialCapacity) {
        this.elements = new Activity[Math.max(initialCapacity, 4)];
        this.size = 0;
    }

    /**
     * Adds an activity to the array. Expands array if capacity is reached.
     */
    public synchronized void add(Activity activity) {
        if (activity == null) return;
        if (size >= elements.length) {
            resize(elements.length * 2);
        }
        elements[size++] = activity;
    }

    /**
     * Finds an activity by its ID.
     */
    public synchronized Activity findById(int id) {
        for (int i = 0; i < size; i++) {
            if (elements[i].getId() == id) {
                return elements[i];
            }
        }
        return null;
    }

    /**
     * Returns the activity at the specified index.
     */
    public synchronized Activity get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size " + size);
        }
        return elements[index];
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    /**
     * Traverses each activity in the array.
     */
    public synchronized void forEach(Consumer<Activity> action) {
        for (int i = 0; i < size; i++) {
            action.accept(elements[i]);
        }
    }

    private void resize(int newCapacity) {
        Activity[] copy = new Activity[newCapacity];
        for (int i = 0; i < size; i++) {
            copy[i] = elements[i];
        }
        elements = copy;
    }
}
