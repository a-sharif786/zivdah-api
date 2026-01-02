package com.zivdah.auth.serviceImpl;

import com.zivdah.auth.dto.ForgotPasswordRequestDTO;
import com.zivdah.auth.dto.ResetPasswordRequestDTO;
import com.zivdah.auth.entity.UserEntity;
import com.zivdah.auth.repository.UserRepository;
import com.zivdah.auth.service.PasswordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class PasswordServiceImpl implements PasswordService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private final Map<Long, String> resetTokenStore = new HashMap<>();

    @Override
    public void forgotPassword(ForgotPasswordRequestDTO request) {
        if (request.getTo() != null) {
            sendForgetPasswordEmail(
                    request
            );
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequestDTO request) {

        String storedToken = resetTokenStore.get(request.getUserId());
        if (storedToken == null ||
                !storedToken.equals(request.getResetToken())) {
            throw new RuntimeException("Invalid or expired reset token");
        }

        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetTokenStore.remove(request.getUserId());
    }

    private void sendForgetPasswordEmail(ForgotPasswordRequestDTO request) {

        String resetUrl = "http://localhost:3000/forget-password?emailId=" + request.getTo();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(request.getTo());
        message.setSubject(request.getSubject());
        log.info("Forgot password email successfully sent to {}", request.getTo());

//        message.setText(
//                request.getBody()
//        );
        message.setText(
                "Dear User,\n\n" +
                        "You requested to forget your password.\n\n" +
                        "Click the link below to forget your password:\n" +
                        resetUrl +
                        "\n\nThis link is valid for 10 minutes.\n\n" +
                        "If you did not request this, please ignore this email.\n\n" +
                        "Regards,\nAuth Service Team"
        );

        mailSender.send(message);
        log.info("Forgot password email successfully sent to {}", request.getTo());
    }

}
