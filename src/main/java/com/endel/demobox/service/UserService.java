package com.endel.demobox.service;

import com.endel.demobox.authentication.RegisterRequestDTO;
import com.endel.demobox.model.User;
import com.endel.demobox.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


import java.util.Optional;
import java.util.Set;



@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void registerUser(RegisterRequestDTO request) {
        if (userRepository.existsByUsername(request.getEmail())) {
            throw new RuntimeException("Username is already taken");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setUsername(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRoles(Set.of("ROLE_USER")); // default role

        userRepository.save(user);
    }

    public User findByUsername(String username){

        Optional<User> optionalUser = userRepository.findByUsername(username);
        return optionalUser.orElse(null);

    }
}