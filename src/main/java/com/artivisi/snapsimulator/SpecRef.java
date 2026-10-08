package com.artivisi.snapsimulator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks code that implements, or a test that verifies, an item of
 * {@code docs/spec-index.json}. Value is {@code <item id>} or
 * {@code <item id>#<request|response>.<field>}. Checked by SpecTraceabilityTest.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
@Repeatable(SpecRef.List.class)
public @interface SpecRef {

    String value();

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
    @interface List {
        SpecRef[] value();
    }
}
