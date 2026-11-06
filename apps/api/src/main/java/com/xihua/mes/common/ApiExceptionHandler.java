package com.xihua.mes.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.xihua.mes.config.form.DynamicFormSchemaException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PlantNotSetException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handlePlantNotSet(PlantNotSetException ex) {
        return new ApiError("PLANT_NOT_SET", ex.getMessage());
    }

    @ExceptionHandler(PlantHeaderInvalidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handlePlantHeaderInvalid(PlantHeaderInvalidException ex) {
        return new ApiError(PlantHeaderInvalidException.CODE, ex.getMessage());
    }

    @ExceptionHandler(DynamicFormSchemaException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiError handleDynamicFormSchema(DynamicFormSchemaException ex) {
        return new ApiError(ex.getCode(), ex.getMessage());
    }
}
