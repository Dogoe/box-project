package com.endel.demobox.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private String firstName;
    @Column
    private String lastName;
    @Column
    private String username;
    @Column
    private String email;
    @Column
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> roles = new HashSet<>();

    // Customer account data (shop). Nullable so ddl-auto can add them to existing rows (e.g. the admin).
    @Column(length = 120)
    private String fullName;

    /** 10-digit Mexican number, normalized. */
    @Column(length = 10)
    private String phone;

    /** Proven by clicking the verification link (or a password reset link); unlocks claiming guest orders. */
    private Boolean emailVerified;

    /** Last delivery address used, to prefill the next checkout. */
    @Column(length = 200)
    private String defaultStreet;
    @Column(length = 120)
    private String defaultNeighborhood;
    @Column(length = 5)
    private String defaultPostalCode;
    @Column(length = 500)
    private String defaultAddressReferences;

    @org.hibernate.annotations.CreationTimestamp
    @Column(updatable = false)
    private java.time.LocalDateTime createdAt;

    public boolean isEmailVerified() {
        return Boolean.TRUE.equals(emailVerified);
    }

}
