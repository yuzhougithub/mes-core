package com.xihua.mes.config.form;

/**
 * Invalid dynamic-form schema (duplicate key, illegal type, forbidden keys, etc.).
 */
public class DynamicFormSchemaException extends RuntimeException {

    public DynamicFormSchemaException(String message) {
        super(message);
    }
}
