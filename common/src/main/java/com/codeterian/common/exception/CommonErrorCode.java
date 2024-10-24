package com.codeterian.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode{

	INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid parameter included"),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not exists"),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
	UNAUTHORIZED_USER(HttpStatus.UNAUTHORIZED, "User is unauthorized"),
	INVALID_ORDER_STATE(HttpStatus.BAD_REQUEST, "User is not in the RunningQueue")
	;

	private final HttpStatus httpStatus;
	private final String message;
}
