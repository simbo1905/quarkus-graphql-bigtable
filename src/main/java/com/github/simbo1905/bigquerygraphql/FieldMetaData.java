package com.github.simbo1905.bigquerygraphql;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @NoArgsConstructor @ToString
@RegisterForReflection
public class FieldMetaData {
    /**
     * Wiring typeName e.g. "Query", "Book"
     */
    String typeName;

    /**
     * Wiring fieldName e.g. "bookById", "author"
     */
    String fieldName;

    /**
     * The BigTable column family
     */
    String family;

    /**
     * The BigTable cell qualifiers
     */
    String qualifiesCsv;

    /**
     * The source parameter (if query) or attribute (if entity) on the GraphQL side e.g., "authorId"
     */
    String gqlAttr;
}
