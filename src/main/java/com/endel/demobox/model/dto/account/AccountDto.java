package com.endel.demobox.model.dto.account;

import lombok.Data;

@Data
public class AccountDto {
    private String fullName;
    private String phone;
    private String email;
    private boolean emailVerified;
    private String defaultStreet;
    private String defaultNeighborhood;
    private String defaultPostalCode;
    private String defaultAddressReferences;
}
