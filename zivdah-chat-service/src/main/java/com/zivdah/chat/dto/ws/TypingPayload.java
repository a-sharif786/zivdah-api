package com.zivdah.chat.dto.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypingPayload {
    private Long conversationId;

    // Named as the Boolean wrapper (not primitive `boolean`) so the JSON property name matches
    // the spec's {conversationId, isTyping} shape exactly for both serialization and
    // deserialization. A primitive `boolean isTyping` field would make Lombok generate
    // isTyping() as its getter, and Jackson's default bean-property naming collapses an isXxx()
    // getter to "typing" (strips "is"), not "isTyping". Boolean wrapper fields don't get that
    // special-cased getter name, so getIsTyping()/setIsTyping() map back to "isTyping" untouched.
    private Boolean isTyping;
}
