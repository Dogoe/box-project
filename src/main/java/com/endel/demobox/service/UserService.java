package com.endel.demobox.service;

import com.endel.demobox.model.User;
import com.endel.demobox.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


import java.util.Optional;



@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User findByUsername(String username){

        Optional<User> optionalUser = userRepository.findByUsername(username);
        return optionalUser.orElse(null);

    }
}