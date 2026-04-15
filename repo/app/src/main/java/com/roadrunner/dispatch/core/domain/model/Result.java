package com.roadrunner.dispatch.core.domain.model;

import java.util.Collections;
import java.util.List;

public class Result<T> {
    private final T data;
    private final List<String> errors;
    private final boolean success;

    private Result(T data, List<String> errors, boolean success) {
        this.data = data;
        this.errors = errors;
        this.success = success;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(data, Collections.emptyList(), true);
    }

    public static <T> Result<T> failure(String error) {
        return new Result<>(null, Collections.singletonList(error), false);
    }

    public static <T> Result<T> failure(List<String> errors) {
        return new Result<>(null, errors, false);
    }

    public T getData() { return data; }
    public List<String> getErrors() { return errors; }
    public boolean isSuccess() { return success; }
    public String getFirstError() { return errors.isEmpty() ? "" : errors.get(0); }
}
