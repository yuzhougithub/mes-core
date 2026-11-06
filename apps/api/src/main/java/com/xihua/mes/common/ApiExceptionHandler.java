package com.xihua.mes.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PlantNotSetException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handlePlantNotSet(PlantNotSetException ex) {
        return new ApiError("PLANT_NOT_SET", ex.getMessage());
    }
}
