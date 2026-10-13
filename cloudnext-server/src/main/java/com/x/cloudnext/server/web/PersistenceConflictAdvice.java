package com.x.cloudnext.server.web;

import java.util.Collections;
import java.util.Map;
import javax.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PersistenceConflictAdvice {
    @ExceptionHandler({OptimisticLockException.class,OptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,String> conflict(Exception ignored){return Collections.singletonMap("message","mapping changed concurrently; refresh and retry");}
}
