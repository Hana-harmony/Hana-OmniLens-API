package com.hana.omnilens.portal;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Service;

import com.hana.omnilens.common.exception.BusinessException;
import com.hana.omnilens.common.exception.ErrorCode;

@Service
public class PortalAccessService {

    private final PortalUserRepository userRepository;

    public PortalAccessService(PortalUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public PortalUser requireUser(HttpServletRequest request) {
        Object userId = request.getAttribute(PortalAuthentication.USER_ID_ATTRIBUTE);
        if (!(userId instanceof String value) || value.isBlank()) {
            throw new BusinessException(ErrorCode.PORTAL_AUTHENTICATION_REQUIRED);
        }
        return userRepository.findByUserId(value)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTAL_AUTHENTICATION_REQUIRED));
    }

    public PortalUser requireAdmin(HttpServletRequest request) {
        PortalUser user = requireUser(request);
        if (user.role() != PortalRole.ADMIN) {
            throw new BusinessException(ErrorCode.PORTAL_ACCESS_DENIED);
        }
        return user;
    }
}
