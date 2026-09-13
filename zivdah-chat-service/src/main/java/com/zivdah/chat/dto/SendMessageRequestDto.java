package com.zivdah.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

// REST fallback for sending a HUMAN-conversation message — shares MessageService#sendMessage
// with the WebSocket handler, so persistence + broadcast never drifts between the two paths.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageRequestDto {

    @NotBlank
    @Size(max = 4000)
    private String message;

    // TEXT/IMAGE/FILE/SYSTEM — defaults to TEXT if omitted/unrecognized.
    private String messageType;

    private String attachmentUrl;
}
