-- Real SMS OTP (see MobileOtpService / SmsOtpProvider). The SMS provider generates the OTP
-- itself and hands back a reference (DataGen's "logid", 13+ chars — too long for the old
-- mobile_otp VARCHAR(10)) that verification must quote back. mobile_otp is no longer written.
--
-- mobile_otp_attempts caps wrong guesses per issued OTP: each verify reserves an attempt
-- atomically before calling the provider, so concurrent guesses can't overshoot the max.
-- otp_generated_at (V1) now also drives the resend cooldown.
ALTER TABLE users ADD COLUMN IF NOT EXISTS mobile_otp_ref      VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS mobile_otp_attempts INT NOT NULL DEFAULT 0;
