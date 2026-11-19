package com.teumta.server.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 아니에요.")
        @Size(max = 254, message = "이메일이 너무 길어요.")
        String email,

        // 영문·숫자·기호 8~64자. BCrypt는 72바이트까지만 쓰므로 한글(글자당 3바이트)은 받지 않는다
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(regexp = "^[\\x21-\\x7E]{8,64}$", message = "비밀번호는 영문·숫자·기호 8~64자예요.")
        String password
) {
}
