package com.alibaba.cloud.ai.manus.tool.browser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AriaElementHolder {
    
    public static class AriaNode {
        public String role;
        public String name;
        public String ref;
        public Map<String, String> props = new HashMap<>();
        public List<Object> children = new ArrayList<>(); // Can be AriaNode or String
        public Map<String, String> attributes = new HashMap<>(); // checked, disabled, expanded, etc.
        
        public AriaNode(String role, String name) {
            this.role = role;
            this.name = name;
        }
        
        public boolean isInteractable() {
            return "link".equals(role) || "button".equals(role) || 
                   "textbox".equals(role) || "checkbox".equals(role) ||
                   "combobox".equals(role) || "radio".equals(role);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(role);
            if (name != null && !name.isEmpty()) {
                sb.append(" \"").append(name).append("\"");
            }
            if (ref != null && !ref.isEmpty()) {
                // Remove "e" prefix if present and use idx instead of ref
                String idx = ref.startsWith("e") ? ref.substring(1) : ref;
                sb.append(" [idx=").append(idx).append("]");
            }
            return sb.toString();
        }
    }
    
    private List<AriaNode> rootNodes = new ArrayList<>();
    private Map<String, AriaNode> refMap = new HashMap<>();
    private Map<String, List<AriaNode>> roleMap = new HashMap<>();
    private Map<String, List<AriaNode>> nameMap = new HashMap<>();
    
    /**
     * Parse ARIA snapshot YAML string
     */
    public static AriaElementHolder parse(String snapshot) {
        AriaElementHolder parser = new AriaElementHolder();
        parser.parseSnapshot(snapshot);
        return parser;
    }
    
    /**
     * Parse the snapshot YAML string
     */
    private void parseSnapshot(String snapshot) {
        String[] lines = snapshot.split("\n");
        Stack<AriaNode> nodeStack = new Stack<>();
        Stack<Integer> indentStack = new Stack<>();
        
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            int indent = getIndent(line);
            String content = line.trim();
            
            // Remove leading dash and spaces
            if (content.startsWith("-")) {
                content = content.substring(1).trim();
            }
            
            // Parse node or property
            if (content.startsWith("/")) {
                // Property: /url: value, /placeholder: value, etc.
                parseProperty(nodeStack.peek(), content);
            } else if (content.startsWith("text:")) {
                // Text node
                String text = content.substring(5).trim();
                if (text.startsWith("\"") && text.endsWith("\"")) {
                    text = text.substring(1, text.length() - 1);
                }
                if (!nodeStack.isEmpty()) {
                    nodeStack.peek().children.add(text);
                }
            } else {
                // Element node: role "name" [attributes]
                AriaNode node = parseNode(content);
                
                // Pop stack until we find the correct parent
                while (!indentStack.isEmpty() && indentStack.peek() >= indent) {
                    indentStack.pop();
                    nodeStack.pop();
                }
                
                // Add to parent or root
                if (nodeStack.isEmpty()) {
                    rootNodes.add(node);
                } else {
                    nodeStack.peek().children.add(node);
                }
                
                nodeStack.push(node);
                indentStack.push(indent);
                
                // Build index maps
                indexNode(node);
            }
        }
    }
    
    /**
     * Parse a node line: role "name" [attributes]
     */
    private AriaNode parseNode(String line) {
        // Pattern: role "name" [attr1=value1] [attr2] [ref=e123]
        Pattern nodePattern = Pattern.compile(
            "^(\\w+)(?:\\s+(\"[^\"]+\"|\\S+))?(.*)$"
        );
        
        Matcher matcher = nodePattern.matcher(line);
        if (!matcher.find()) {
            return new AriaNode("generic", "");
        }
        
        String role = matcher.group(1);
        String name = matcher.group(2);
        String attributes = matcher.group(3);
        
        // Clean name
        if (name != null) {
            name = name.trim();
            if (name.startsWith("\"") && name.endsWith("\"")) {
                name = name.substring(1, name.length() - 1);
            }
        } else {
            name = "";
        }
        
        AriaNode node = new AriaNode(role, name);
        
        // Parse attributes: [idx=3], [ref=e3], [checked], [disabled], etc.
        if (attributes != null) {
            Pattern attrPattern = Pattern.compile("\\[(\\w+)(?:=([^\\]]+))?\\]");
            Matcher attrMatcher = attrPattern.matcher(attributes);
            while (attrMatcher.find()) {
                String attrName = attrMatcher.group(1);
                String attrValue = attrMatcher.group(2);
                
                if ("idx".equals(attrName) || "ref".equals(attrName)) {
                    // Handle both idx and ref for backwards compatibility
                    // If idx, store as-is; if ref with "e" prefix, remove the "e"
                    if ("idx".equals(attrName)) {
                        node.ref = attrValue != null ? attrValue : "";
                    }
                    else if ("ref".equals(attrName) && attrValue != null) {
                        // Remove "e" prefix if present
                        node.ref = attrValue.startsWith("e") ? attrValue.substring(1) : attrValue;
                    }
                } else {
                    node.attributes.put(attrName, attrValue != null ? attrValue : "true");
                }
            }
        }
        
        return node;
    }
    
    /**
     * Parse property: /url: value, /placeholder: value
     */
    private void parseProperty(AriaNode node, String line) {
        if (node == null) return;
        
        Pattern propPattern = Pattern.compile("^/(\\w+):\\s*(.+)$");
        Matcher matcher = propPattern.matcher(line);
        if (matcher.find()) {
            String propName = matcher.group(1);
            String propValue = matcher.group(2).trim();
            if (propValue.startsWith("\"") && propValue.endsWith("\"")) {
                propValue = propValue.substring(1, propValue.length() - 1);
            }
            node.props.put(propName, propValue);
        }
    }
    
    /**
     * Get indent level (number of spaces before content)
     */
    private int getIndent(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                indent++;
            } else {
                break;
            }
        }
        return indent;
    }
    
    /**
     * Index node for fast lookup
     */
    private void indexNode(AriaNode node) {
        // Index by ref
        if (node.ref != null && !node.ref.isEmpty()) {
            refMap.put(node.ref, node);
        }
        
        // Index by role
        roleMap.computeIfAbsent(node.role, k -> new ArrayList<>()).add(node);
        
        // Index by name
        if (node.name != null && !node.name.isEmpty()) {
            nameMap.computeIfAbsent(node.name, k -> new ArrayList<>()).add(node);
        }
        
        // Recursively index children
        for (Object child : node.children) {
            if (child instanceof AriaNode) {
                indexNode((AriaNode) child);
            }
        }
    }
    
    /**
     * Get node by ref
     */
    public AriaNode getByRef(String ref) {
        return refMap.get(ref);
    }
    
    /**
     * Get node by refId (alias for getByRef)
     * @param refId The ref identifier
     * @return AriaNode with the given refId, or null if not found
     */
    public AriaNode getByRefId(String refId) {
        return refMap.get(refId);
    }
    
    /**
     * Get all nodes by role
     */
    public List<AriaNode> getByRole(String role) {
        return roleMap.getOrDefault(role, Collections.emptyList());
    }
    
    /**
     * Get all nodes by name
     */
    public List<AriaNode> getByName(String name) {
        return nameMap.getOrDefault(name, Collections.emptyList());
    }
    
    /**
     * Get node by role and name
     */
    public List<AriaNode> getByRoleAndName(String role, String name) {
        return getByRole(role).stream()
            .filter(node -> name.equals(node.name))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all interactable elements (links, buttons, textboxes, etc.)
     */
    public List<AriaNode> getInteractableElements() {
        List<AriaNode> result = new ArrayList<>();
        collectInteractable(rootNodes, result);
        return result;
    }
    
    private void collectInteractable(List<AriaNode> nodes, List<AriaNode> result) {
        for (AriaNode node : nodes) {
            if (node.isInteractable()) {
                result.add(node);
            }
            for (Object child : node.children) {
                if (child instanceof AriaNode) {
                    collectInteractable(Collections.singletonList((AriaNode) child), result);
                }
            }
        }
    }
    
    /**
     * Get all links
     */
    public List<AriaNode> getLinks() {
        return getByRole("link");
    }
    
    /**
     * Get all buttons
     */
    public List<AriaNode> getButtons() {
        return getByRole("button");
    }
    
    /**
     * Get all textboxes
     */
    public List<AriaNode> getTextboxes() {
        return getByRole("textbox");
    }
    
    /**
     * Get root nodes
     */
    public List<AriaNode> getRootNodes() {
        return rootNodes;
    }
    
    /**
     * Get all refs
     */
    public Set<String> getAllRefs() {
        return refMap.keySet();
    }
    
    /**
     * Convert back to YAML string (simplified)
     */
    public String toYaml() {
        StringBuilder sb = new StringBuilder();
        for (AriaNode node : rootNodes) {
            toYaml(node, sb, "");
        }
        return sb.toString();
    }
    
    private void toYaml(AriaNode node, StringBuilder sb, String indent) {
        sb.append(indent).append("- ").append(node.role);
        if (node.name != null && !node.name.isEmpty()) {
            sb.append(" \"").append(node.name).append("\"");
        }
        if (node.ref != null && !node.ref.isEmpty()) {
            // Use idx instead of ref, remove "e" prefix if present
            String idx = node.ref.startsWith("e") ? node.ref.substring(1) : node.ref;
            sb.append(" [idx=").append(idx).append("]");
        }
        for (Map.Entry<String, String> attr : node.attributes.entrySet()) {
            if (!"ref".equals(attr.getKey())) {
                if ("true".equals(attr.getValue())) {
                    sb.append(" [").append(attr.getKey()).append("]");
                } else {
                    sb.append(" [").append(attr.getKey()).append("=").append(attr.getValue()).append("]");
                }
            }
        }
        sb.append("\n");
        
        // Add properties
        for (Map.Entry<String, String> prop : node.props.entrySet()) {
            sb.append(indent).append("  - /").append(prop.getKey()).append(": ").append(prop.getValue()).append("\n");
        }
        
        // Add children
        for (Object child : node.children) {
            if (child instanceof String) {
                sb.append(indent).append("  - text: \"").append(child).append("\"\n");
            } else if (child instanceof AriaNode) {
                toYaml((AriaNode) child, sb, indent + "  ");
            }
        }
    }
    
    /**
     * Find node by partial name match
     */
    public List<AriaNode> findByNameContaining(String partialName) {
        return nameMap.values().stream()
            .flatMap(List::stream)
            .filter(node -> node.name != null && node.name.contains(partialName))
            .collect(Collectors.toList());
    }
    
    /**
     * Get node URL (for links)
     */
    public String getUrl(AriaNode node) {
        return node.props.get("url");
    }
    
    /**
     * Generate locator selector for a node
     */
    public String generateLocator(AriaNode node) {
        if (node.ref != null && !node.ref.isEmpty()) {
            return "aria-ref=" + node.ref;
        }
        
        if ("link".equals(node.role) && node.name != null && !node.name.isEmpty()) {
            return "getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(\"" + node.name + "\"))";
        }
        
        if ("button".equals(node.role) && node.name != null && !node.name.isEmpty()) {
            return "getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(\"" + node.name + "\"))";
        }
        
        if ("textbox".equals(node.role) && node.name != null && !node.name.isEmpty()) {
            return "getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(\"" + node.name + "\"))";
        }
        
        return null;
    }
    
    /**
     * Add a ref to a specific node
     * @param node The node to add ref to
     * @param ref The ref identifier to assign
     * @return true if ref was added successfully, false if node already has a ref
     */
    public boolean addRefToNode(AriaNode node, String ref) {
        if (node == null || ref == null || ref.isEmpty()) {
            return false;
        }
        
        // If node already has a ref, don't overwrite unless it's the same
        if (node.ref != null && !node.ref.isEmpty()) {
            if (node.ref.equals(ref)) {
                return true; // Already has this ref
            }
            return false; // Already has a different ref
        }
        
        // Remove from old ref index if it exists
        String oldRef = node.ref;
        if (oldRef != null && refMap.containsKey(oldRef)) {
            refMap.remove(oldRef);
        }
        
        // Add new ref
        node.ref = ref;
        refMap.put(ref, node);
        
        return true;
    }
    
    /**
     * Add a ref to a node found by role and name
     * @param role The role of the node
     * @param name The name of the node
     * @param ref The ref identifier to assign
     * @return true if ref was added successfully, false if node not found or already has a ref
     */
    public boolean addRefToNodeByRoleAndName(String role, String name, String ref) {
        List<AriaNode> nodes = getByRoleAndName(role, name);
        if (nodes.isEmpty()) {
            return false;
        }
        
        // Use the first matching node
        return addRefToNode(nodes.get(0), ref);
    }
    
    /**
     * Automatically assign refs to all nodes that don't have refs
     * Generates refs without "e" prefix (1, 2, 3, etc.)
     * @return Number of refs added
     */
    public int addRefsToNodesWithoutRefs() {
        int counter = 1;
        int addedCount = 0;
        
        // Find all nodes without refs
        List<AriaNode> nodesWithoutRefs = new ArrayList<>();
        collectNodesWithoutRefs(rootNodes, nodesWithoutRefs);
        
        // Assign refs (without "e" prefix)
        for (AriaNode node : nodesWithoutRefs) {
            String ref = String.valueOf(counter);
            // Make sure ref doesn't already exist
            while (refMap.containsKey(ref) || refMap.containsKey("e" + ref)) {
                counter++;
                ref = String.valueOf(counter);
            }
            
            if (addRefToNode(node, ref)) {
                addedCount++;
                counter++;
            }
        }
        
        return addedCount;
    }
    
    /**
     * Recursively collect all nodes without refs
     */
    private void collectNodesWithoutRefs(List<AriaNode> nodes, List<AriaNode> result) {
        for (AriaNode node : nodes) {
            if (node.ref == null || node.ref.isEmpty()) {
                result.add(node);
            }
            
            // Recursively check children
            for (Object child : node.children) {
                if (child instanceof AriaNode) {
                    collectNodesWithoutRefs(Collections.singletonList((AriaNode) child), result);
                }
            }
        }
    }
    
    /**
     * Add ref to a specific node by finding it in the tree
     * @param predicate Function to identify the target node
     * @param ref The ref identifier to assign
     * @return true if ref was added successfully, false if node not found or already has a ref
     */
    public boolean addRefToNodeMatching(java.util.function.Predicate<AriaNode> predicate, String ref) {
        AriaNode targetNode = findNode(rootNodes, predicate);
        if (targetNode == null) {
            return false;
        }
        return addRefToNode(targetNode, ref);
    }
    
    /**
     * Recursively find a node matching the predicate
     */
    private AriaNode findNode(List<AriaNode> nodes, java.util.function.Predicate<AriaNode> predicate) {
        for (AriaNode node : nodes) {
            if (predicate.test(node)) {
                return node;
            }
            
            // Recursively check children
            for (Object child : node.children) {
                if (child instanceof AriaNode) {
                    AriaNode found = findNode(Collections.singletonList((AriaNode) child), predicate);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }
}
