package com.example.backend.common.realtime;

import com.example.backend.auth.security.AppUserDetailsService;
import com.example.backend.auth.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketJwtChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_NOTIFICATIONS_DESTINATION = "/user/queue/notifications";

    private final JwtService jwtService;
    private final AppUserDetailsService appUserDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (command == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            accessor.setUser(authenticate(accessor));
            return message;
        }

        if (StompCommand.DISCONNECT.equals(command)) {
            return message;
        }

        Authentication authentication = (Authentication) accessor.getUser();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("WebSocket authentication is required");
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            if (!USER_NOTIFICATIONS_DESTINATION.equals(destination)) {
                throw new AccessDeniedException("WebSocket subscription is not allowed");
            }
        }

        if (StompCommand.SEND.equals(command)) {
            throw new AccessDeniedException("Client WebSocket messages are not enabled");
        }

        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String authorizationHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Missing WebSocket bearer token");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());

        try {
            String username = jwtService.extractUsername(token);
            UserDetails userDetails = appUserDetailsService.loadUserByUsername(username);

            if (!userDetails.isEnabled() || !jwtService.isTokenValid(token, userDetails.getUsername())) {
                throw new AccessDeniedException("Invalid WebSocket bearer token");
            }

            return UsernamePasswordAuthenticationToken.authenticated(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException exception) {
            throw new AccessDeniedException("Invalid WebSocket bearer token", exception);
        }
    }
}
