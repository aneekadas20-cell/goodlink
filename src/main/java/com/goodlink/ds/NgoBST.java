package com.goodlink.ds;

import com.goodlink.model.NGO;
import java.util.function.Consumer;

/**
 * Hand-written Binary Search Tree storing NGOs keyed by integer id.
 * Supports: insert, search, in-order print / traversal.
 * Do NOT use java.util collections for core logic.
 */
public class NgoBST {

    public static class BSTNode {
        public NGO ngo;
        public BSTNode left;
        public BSTNode right;

        public BSTNode(NGO ngo) {
            this.ngo = ngo;
            this.left = null;
            this.right = null;
        }
    }

    private BSTNode root;
    private int size;

    public NgoBST() {
        this.root = null;
        this.size = 0;
    }

    /**
     * Inserts an NGO into the BST keyed by ngo.getId().
     */
    public synchronized void insert(NGO ngo) {
        if (ngo == null) return;
        root = insertRecursive(root, ngo);
    }

    private BSTNode insertRecursive(BSTNode current, NGO ngo) {
        if (current == null) {
            size++;
            return new BSTNode(ngo);
        }
        if (ngo.getId() < current.ngo.getId()) {
            current.left = insertRecursive(current.left, ngo);
        } else if (ngo.getId() > current.ngo.getId()) {
            current.right = insertRecursive(current.right, ngo);
        } else {
            // Update existing record
            current.ngo = ngo;
        }
        return current;
    }

    /**
     * Searches for an NGO by ID in O(log n) average time.
     */
    public synchronized NGO search(int id) {
        BSTNode current = root;
        while (current != null) {
            if (id == current.ngo.getId()) {
                return current.ngo;
            } else if (id < current.ngo.getId()) {
                current = current.left;
            } else {
                current = current.right;
            }
        }
        return null;
    }

    /**
     * In-order traversal executing an action on each NGO in ascending key order.
     */
    public synchronized void inOrder(Consumer<NGO> action) {
        inOrderRecursive(root, action);
    }

    private void inOrderRecursive(BSTNode node, Consumer<NGO> action) {
        if (node != null) {
            inOrderRecursive(node.left, action);
            action.accept(node.ngo);
            inOrderRecursive(node.right, action);
        }
    }

    /**
     * In-order print returning a formatted string of all NGOs in sorted order.
     */
    public synchronized String inOrderPrint() {
        if (root == null) {
            return "No NGOs registered yet.";
        }
        StringBuilder sb = new StringBuilder();
        inOrder(ngo -> {
            sb.append(String.format("• [#%d] *%s*%n  Category: %s%n  City: %s%n  Contact: %s%n%n",
                    ngo.getId(), ngo.getName(), ngo.getCategory(), ngo.getCity(), ngo.getEmail()));
        });
        return sb.toString();
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public BSTNode getRoot() {
        return root;
    }
}
