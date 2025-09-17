package com.endel.demobox.authentication;

import lombok.Data;

@Data
public class LoginRequestDTO {
    private String username;
    private String email;
    private String password;
}
