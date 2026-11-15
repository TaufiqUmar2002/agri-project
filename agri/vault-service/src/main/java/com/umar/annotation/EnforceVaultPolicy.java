package com.umar.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnforceVaultPolicy {

    /**
     * The DataClass name target (e.g., 'farmer.national_id'). Supports SpEL expressions.
     */
    String dataClass() default "";

    /**
     * The specific business intent context variable or hardcoded string (e.g., '#request.purpose').
     */
    String purpose() default "";

    /**
     * The target action classification: 'TOKENIZE' or 'DETOKENIZE'.
     */
    String operation() default "";


    String contextRef() default "";

    int batchSize() default 100;

}
