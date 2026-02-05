package com.jher235.jfocus.constant;

import java.util.Set;

public class JdkKnownTypes {
    public static final Set<String> IGNORED_SET = Set.of(
        // 1. Primitives
        "int", "long", "boolean", "double", "float", "char", "byte", "short", "void",

        // 2. Wrappers & Core
        "String", "Integer", "Long", "Boolean", "Double", "Float", "Object", "Class",
        "System", "Math", "StringBuilder", "StringBuffer", "Enum",

        // 3. Collections & Data Structures
        "List", "Map", "Set", "Queue", "Deque", "Collection", "Collections",
        "Arrays", "Iterable", "Iterator", "HashMap", "HashSet", "ArrayList", "LinkedList",

        // 4. Utilities
        "Optional", "Objects", "UUID", "Random", "Base64",

        // 5. Time (Java 8+)
        "LocalDate", "LocalDateTime", "LocalTime", "ZonedDateTime", "Instant", "Duration", "Period",

        // 6. IO & NIO
        "File", "Path", "Paths", "Files", "InputStream", "OutputStream", "Reader", "Writer",

        // 7. Streams & Functional
        "Stream", "Collectors", "Function", "Supplier", "Consumer", "Predicate", "Runnable", "Comparator",

        // 8. Logging
        "Logger", "LoggerFactory", "Slf4j",

        // 9. Keywords
        "var", "val"
    );

    // Prevent instantiation
    private JdkKnownTypes() {}

    /**
     * Checks if the type name is a known JDK type or common library type that should be ignored.
     */
    public static boolean contains(String typeName) {
        return IGNORED_SET.contains(typeName);
    }
}
