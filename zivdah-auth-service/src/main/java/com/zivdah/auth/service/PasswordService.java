package com.zivdah.auth.service;

import com.zivdah.auth.dto.ForgotPasswordRequestDTO;
import com.zivdah.auth.dto.ResetPasswordRequestDTO;

public interface PasswordService {

    void forgotPassword(ForgotPasswordRequestDTO request);

    void resetPassword(ResetPasswordRequestDTO request);
}
