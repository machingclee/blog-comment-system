package com.machingclee.blogcomment.common.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor(access = AccessLevel.NONE)
public class ApiResponse<T> {
    private Boolean success;
    private String errorMessage;
    private T result;

    // region factories
    public static <T> ApiResponse<T> success(SuccessParam<T> param) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setResult(param.getPayload());
        return response;
    }

    public static <T> ApiResponse<T> success(T object) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setResult(object);
        return response;
    }

    public static <T> ApiResponse<T> failed(FailedParam<T> param) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setResult(param.getPayload());
        response.setErrorMessage(param.errorMessage);

        return response;
    }


    @Builder
    @Data
    public static class SuccessParam<T> {
        private T payload;
    }

    @Builder
    @Data
    public static class FailedParam<T> {
        private T payload;
        private String errorMessage;
    }
    // endregion
}
