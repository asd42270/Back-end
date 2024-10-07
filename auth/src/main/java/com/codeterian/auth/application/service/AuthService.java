package com.codeterian.auth.application.service;

import com.codeterian.auth.infrastructure.util.CustomUserDetailsService;
import com.codeterian.auth.infrastructure.util.UserPrincipal;
import com.codeterian.auth.presentation.LoginRequestDto;
import com.codeterian.common.infrastructure.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final RedisService redisService;
    private final JwtTokenGenerator jwtTokenGenerator;
    private final CustomUserDetailsService customUserDetailsService;

    public String login(LoginRequestDto loginRequestDto) {

        UserPrincipal userDetails = (UserPrincipal) customUserDetailsService.loadUserByUsername(loginRequestDto.email());

        // 사용자 인증 생성
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequestDto.email(),
                        loginRequestDto.password()
                )
        );

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();

        UserDto userDto = new UserDto(user.getUsername(),
                user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()));

        redisService.setValue("user:" + loginRequestDto.email(), userDto);

        // JWT 토큰 생성 및 반환
        return jwtTokenGenerator.createJwtToken(userDetails.getUserFindAllInfoResponseDto().id(),
                user.getUsername(),
                userDetails.getUserFindAllInfoResponseDto().name(),
                user.getAuthorities());
    }

}
