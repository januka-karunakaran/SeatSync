package com.seatsync.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.common.model.ProtocolConstants;

import java.io.IOException;

/**
 * Standard Jackson JSON serialization and deserialization utility for SeatSync wire messages.
 * Enforces UTF-8 framing rules and maximum frame size (16 KiB).
 */
public final class ProtocolJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ProtocolJson() {
        // utility class
    }

    /**
     * @return Shared Jackson ObjectMapper instance
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Serializes any message object to a UTF-8 JSON string without a trailing newline.
     *
     * @param message Message object to serialize
     * @return Compact JSON representation
     * @throws IllegalArgumentException if serialization fails or byte size exceeds 16 KiB
     */
    public static String serialize(Object message) {
        try {
            String json = MAPPER.writeValueAsString(message);
            byte[] bytes = json.getBytes(ProtocolConstants.CHARSET);
            if (bytes.length > ProtocolConstants.MAX_FRAME_BYTES) {
                throw new IllegalArgumentException(
                    "Serialized frame exceeds max frame limit (" + bytes.length + " > " + ProtocolConstants.MAX_FRAME_BYTES + " bytes)"
                );
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize message: " + e.getMessage(), e);
        }
    }

    /**
     * Serializes any message object to a LF-terminated UTF-8 wire line.
     *
     * @param message Message object to serialize
     * @return Compact JSON line ending with \n
     */
    public static String serializeToWire(Object message) {
        return serialize(message) + ProtocolConstants.LINE_DELIMITER;
    }

    /**
     * Deserializes a JSON string into a specific typed message.
     *
     * @param json  JSON string
     * @param clazz Target class
     * @param <T>   Target type
     * @return Deserialized object
     * @throws IllegalArgumentException if parsing fails or frame exceeds 16 KiB
     */
    public static <T> T deserialize(String json, Class<T> clazz) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string must not be null");
        }
        byte[] bytes = json.getBytes(ProtocolConstants.CHARSET);
        if (bytes.length > ProtocolConstants.MAX_FRAME_BYTES) {
            throw new IllegalArgumentException(
                "Incoming frame exceeds max frame limit (" + bytes.length + " > " + ProtocolConstants.MAX_FRAME_BYTES + " bytes)"
            );
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize message: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes a polymorphic protocol message by inspecting its "type" field.
     *
     * @param json JSON frame string
     * @return Message instance
     */
    public static Message deserializeMessage(String json) {
        return deserialize(json, Message.class);
    }

    /**
     * Parses a JSON string into a tree model JsonNode.
     *
     * @param json JSON frame string
     * @return JsonNode tree
     */
    public static JsonNode parseTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON syntax: " + e.getMessage(), e);
        }
    }
}
