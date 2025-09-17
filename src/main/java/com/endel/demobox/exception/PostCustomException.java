package com.endel.demobox.exception;


import lombok.Getter;

@Getter
public class PostCustomException extends RuntimeException{
    private  int status;
    public PostCustomException(int status, String message){
        super(message);
        this.status = status;
    }
}
