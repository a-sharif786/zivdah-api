package com.zivdah.chat.client.dto;

import lombok.Getter;
import lombok.Setter;

// Mirrors auth-service's own InternalUserInfoDTO (id, name, email, mobile) returned by its
// unauthenticated GET /auth/internal/users/{userId} endpoint (see AuthServiceClient's Javadoc).
// Not read by any Phase 4 intent yet — no intent in this phase personalizes a reply with the
// customer's display name — kept narrow now for a future order-context panel (Phase 6) that
// needs to resolve a vendor/delivery-boy/customer name from an id, the same way
// zivdah-order-service's invoice generation already does.
@Getter
@Setter
public class InternalUserInfoDto {
    private Long id;
    private String name;
    private String email;
    private String mobile;
}
