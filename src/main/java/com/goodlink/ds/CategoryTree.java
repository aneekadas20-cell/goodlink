package com.goodlink.ds;

import java.util.function.Consumer;

/**
 * Hand-written general tree for NGO categories and subcategories.
 * Example hierarchy:
 * NGO Categories
 * ├── Elder Care
 * │   ├── Old Age Homes
 * │   ├── Day Care Centers
 * │   └── Palliative Care
 * ├── Child Welfare
 * │   ├── Orphanages
 * │   └── Street Schooling
 * └── Health & Nutrition
 *     ├── Blood Banks
 *     └── Food Rescue
 */
public class CategoryTree {

    public static class CategoryNode {
        private final String name;
        private final MyLinkedList<CategoryNode> children;

        public CategoryNode(String name) {
            this.name = name;
            this.children = new MyLinkedList<>();
        }

        public String getName() {
            return name;
        }

        public MyLinkedList<CategoryNode> getChildren() {
            return children;
        }

        public void addChild(CategoryNode child) {
            this.children.add(child);
        }
    }

    private final CategoryNode root;

    public CategoryTree(String rootName) {
        this.root = new CategoryNode(rootName);
    }

    public CategoryNode getRoot() {
        return root;
    }

    /**
     * Adds a child category under the specified parent category name.
     */
    public synchronized boolean addCategory(String parentName, String newCategoryName) {
        CategoryNode parent = findNode(root, parentName);
        if (parent != null) {
            parent.addChild(new CategoryNode(newCategoryName));
            return true;
        }
        return false;
    }

    /**
     * Finds a category node by case-insensitive name.
     */
    public synchronized CategoryNode find(String name) {
        return findNode(root, name);
    }

    private CategoryNode findNode(CategoryNode current, String targetName) {
        if (current == null) return null;
        if (current.getName().equalsIgnoreCase(targetName)) {
            return current;
        }
        final CategoryNode[] found = new CategoryNode[1];
        current.getChildren().forEach(child -> {
            if (found[0] == null) {
                CategoryNode res = findNode(child, targetName);
                if (res != null) {
                    found[0] = res;
                }
            }
        });
        return found[0];
    }

    /**
     * Formats the tree hierarchy as a text diagram.
     */
    public synchronized String printTree() {
        StringBuilder sb = new StringBuilder();
        printTreeRecursive(root, "", true, sb);
        return sb.toString();
    }

    private void printTreeRecursive(CategoryNode node, String prefix, boolean isTail, StringBuilder sb) {
        sb.append(prefix).append(isTail ? "└── " : "├── ").append(node.getName()).append("\n");
        int count = node.getChildren().size();
        final int[] index = {0};
        node.getChildren().forEach(child -> {
            index[0]++;
            boolean childTail = (index[0] == count);
            printTreeRecursive(child, prefix + (isTail ? "    " : "│   "), childTail, sb);
        });
    }

    public void forEach(Consumer<CategoryNode> action) {
        traverse(root, action);
    }

    private void traverse(CategoryNode node, Consumer<CategoryNode> action) {
        if (node != null) {
            action.accept(node);
            node.getChildren().forEach(child -> traverse(child, action));
        }
    }
}
