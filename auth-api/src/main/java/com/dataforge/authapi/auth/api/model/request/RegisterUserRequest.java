package com.dataforge.authapi.auth.api.model.request;

import com.dataforge.authapi.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

public record RegisterUserRequest(

        @NotBlank(message = "Email must not be blank")
        @Email(
                regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                message = "Email must be valid"
        )
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @NotBlank(message = "Password must not be blank")
        @CodePointLength(
                min = 15,
                message = "Password must contain at least 15 characters"
        )
        @MaxUtf8Bytes(
                value = 72,
                message = "Password must not exceed 72 UTF-8 bytes"
        )
        String password

) {}