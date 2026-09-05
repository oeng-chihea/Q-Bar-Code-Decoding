package com.excel.reconciler.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentifierCanonicalizerTest {

    @Test
    void canonicalizesUnicodeFormatCharactersAndKhmerDigitsForComparisonOnly() {
        String raw = " ១២-៣ ABC\u200B ";

        assertEquals("123abc", IdentifierCanonicalizer.canonicalize(raw));
        assertEquals(" ១២-៣ ABC\u200B ", raw);
    }

    @Test
    void canonicalizesCanonicalEquivalentUnicodeSequences() {
        assertEquals("é", IdentifierCanonicalizer.canonicalize("e\u0301"));
    }
}
