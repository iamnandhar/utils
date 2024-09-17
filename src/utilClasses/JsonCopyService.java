package src.utilClasses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JsonCopyService {

    private static final Logger logger = LoggerFactory.getLogger(JsonCopyService.class);

    private Properties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String PROPERTIES_FILE = "config.properties"; // Hardcoded file name

    // Initialize properties after bean creation
    @PostConstruct
    public void init() {
        props = new Properties();
        try {
            props.load(new FileInputStream(PROPERTIES_FILE));
        } catch (IOException e) {
            logger.error("Error loading properties file: {}", PROPERTIES_FILE, e);
        }
    }

    // Method to perform the copy operation on the DTO
    public <T> T copyValuesInDto(T dto) {
        try {
            // Convert DTO to JsonNode
            JsonNode rootNode = mapper.convertValue(dto, JsonNode.class);

            // Process each property
            for (String sourcePath : props.stringPropertyNames()) {
                String destPaths = props.getProperty(sourcePath);
                List<String> destinations = Arrays.asList(destPaths.split(","));

                // Get source values
                List<JsonNode> sourceValues = getNodesByPath(rootNode, sourcePath);

                if (!sourceValues.isEmpty()) {
                    // For each destination path
                    for (String destPath : destinations) {
                        // Set values to destination path
                        setValuesByPath(rootNode, destPath, sourceValues);
                    }
                } else {
                    // Handle missing source path
                    logger.warn("Source path not found or is null: {}", sourcePath);
                }
            }

            // Convert JsonNode back to DTO
            return mapper.treeToValue(rootNode, (Class<T>) dto.getClass());

        } catch (Exception e) {
            logger.error("Error during copyValuesInDto", e);
            return dto; // Return original DTO in case of error
        }
    }

    // Method to get nodes by path, supports wildcards and indices
    private List<JsonNode> getNodesByPath(JsonNode currentNode, String path) {
        List<JsonNode> resultNodes = new ArrayList<>();
        try {
            getNodesByPathRecursive(currentNode, path.split("\\."), 0, resultNodes);
        } catch (Exception e) {
            logger.error("Error in getNodesByPath for path: {}", path, e);
        }
        return resultNodes;
    }

    private void getNodesByPathRecursive(JsonNode currentNode, String[] parts, int index, List<JsonNode> resultNodes) {
        if (currentNode == null) {
            return;
        }
        if (index >= parts.length) {
            resultNodes.add(currentNode);
            return;
        }
        String part = parts[index];
        Matcher matcher = Pattern.compile("^(\\w+)(\\[(\\d+|\\*)\\])?$").matcher(part);
        if (matcher.matches()) {
            String fieldName = matcher.group(1);
            String indexPart = matcher.group(3);

            JsonNode childNode = currentNode.get(fieldName);

            if (childNode == null || childNode.isMissingNode()) {
                return;
            }

            if (indexPart == null) {
                // No index, proceed to next level
                getNodesByPathRecursive(childNode, parts, index + 1, resultNodes);
            } else if (indexPart.equals("*")) {
                // Wildcard, iterate over all elements
                if (childNode.isArray()) {
                    for (JsonNode element : childNode) {
                        getNodesByPathRecursive(element, parts, index + 1, resultNodes);
                    }
                }
            } else {
                // Specific index
                int arrayIndex = Integer.parseInt(indexPart);
                if (childNode.isArray() && childNode.size() > arrayIndex) {
                    JsonNode element = childNode.get(arrayIndex);
                    getNodesByPathRecursive(element, parts, index + 1, resultNodes);
                } else {
                    // Index out of bounds
                    logger.warn("Index {} out of bounds for array at path: {}", arrayIndex, String.join(".", Arrays.copyOfRange(parts, 0, index + 1)));
                }
            }
        } else {
            // Invalid path syntax
            logger.error("Invalid path syntax at part: {}", part);
        }
    }

    // Method to set values by path, supports wildcards and indices
    private void setValuesByPath(JsonNode currentNode, String path, List<JsonNode> values) {
        try {
            setValuesByPathRecursive(currentNode, path.split("\\."), 0, values);
        } catch (Exception e) {
            logger.error("Error in setValuesByPath for path: {}", path, e);
        }
    }

    private void setValuesByPathRecursive(JsonNode currentNode, String[] parts, int index, List<JsonNode> values) {
        if (currentNode == null) {
            return;
        }
        if (index >= parts.length) {
            // Should not happen
            return;
        }
        String part = parts[index];
        Matcher matcher = Pattern.compile("^(\\w+)(\\[(\\d+|\\*)\\])?$").matcher(part);
        if (!matcher.matches()) {
            logger.error("Invalid path syntax at part: {}", part);
            return;
        }
        String fieldName = matcher.group(1);
        String indexPart = matcher.group(3);

        if (index == parts.length - 1) {
            // Last part of the path
            if (indexPart == null) {
                // No index, set value directly
                ((ObjectNode) currentNode).set(fieldName, values.get(0).deepCopy());
            } else if (indexPart.equals("*")) {
                // Wildcard, create or get array
                ArrayNode arrayNode;
                if (currentNode.has(fieldName) && currentNode.get(fieldName).isArray()) {
                    arrayNode = (ArrayNode) currentNode.get(fieldName);
                } else {
                    arrayNode = mapper.createArrayNode();
                    ((ObjectNode) currentNode).set(fieldName, arrayNode);
                }
                // Ensure array size matches values size
                while (arrayNode.size() < values.size()) {
                    arrayNode.addNull();
                }
                for (int i = 0; i < values.size(); i++) {
                    arrayNode.set(i, values.get(i).deepCopy());
                }
            } else {
                // Specific index
                int arrayIndex = Integer.parseInt(indexPart);
                ArrayNode arrayNode;
                if (currentNode.has(fieldName) && currentNode.get(fieldName).isArray()) {
                    arrayNode = (ArrayNode) currentNode.get(fieldName);
                } else {
                    arrayNode = mapper.createArrayNode();
                    ((ObjectNode) currentNode).set(fieldName, arrayNode);
                }
                // Ensure array size
                while (arrayNode.size() <= arrayIndex) {
                    arrayNode.addNull();
                }
                arrayNode.set(arrayIndex, values.get(0).deepCopy());
            }
        } else {
            // Intermediate part of the path
            JsonNode childNode = currentNode.get(fieldName);
            if (childNode == null || childNode.isMissingNode()) {
                if (indexPart != null) {
                    // Create array
                    childNode = mapper.createArrayNode();
                } else {
                    childNode = mapper.createObjectNode();
                }
                ((ObjectNode) currentNode).set(fieldName, childNode);
            }

            if (indexPart == null) {
                // No index, proceed to next level
                setValuesByPathRecursive(childNode, parts, index + 1, values);
            } else if (indexPart.equals("*")) {
                // Wildcard
                if (childNode.isArray()) {
                    ArrayNode arrayNode = (ArrayNode) childNode;
                    // If values.size() > 1, match source values with array elements
                    if (values.size() > 1) {
                        // Ensure array size matches values size
                        while (arrayNode.size() < values.size()) {
                            arrayNode.add(mapper.createObjectNode());
                        }
                        for (int i = 0; i < values.size(); i++) {
                            JsonNode arrayElement = arrayNode.get(i);
                            setValuesByPathRecursive(arrayElement, parts, index + 1, Collections.singletonList(values.get(i)));
                        }
                    } else {
                        // Single value, set to all array elements
                        for (int i = 0; i < arrayNode.size(); i++) {
                            JsonNode arrayElement = arrayNode.get(i);
                            setValuesByPathRecursive(arrayElement, parts, index + 1, values);
                        }
                    }
                } else {
                    logger.error("Expected array at path: {}", String.join(".", Arrays.copyOfRange(parts, 0, index + 1)));
                }
            } else {
                // Specific index
                int arrayIndex = Integer.parseInt(indexPart);
                if (childNode.isArray()) {
                    ArrayNode arrayNode = (ArrayNode) childNode;
                    // Ensure array size
                    while (arrayNode.size() <= arrayIndex) {
                        arrayNode.add(mapper.createObjectNode());
                    }
                    JsonNode arrayElement = arrayNode.get(arrayIndex);
                    setValuesByPathRecursive(arrayElement, parts, index + 1, values);
                } else {
                    logger.error("Expected array at path: {}", String.join(".", Arrays.copyOfRange(parts, 0, index + 1)));
                }
            }
        }
    }
}
