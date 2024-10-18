package com.codeterian.queue.application.service;

import com.codeterian.common.infrastructure.util.Passport;
import com.codeterian.common.infrastructure.util.PassportContextHolder;
import com.codeterian.queue.application.feign.OrderService;
import com.codeterian.queue.application.feign.dto.OrderAddRequestDto;
import com.codeterian.queue.application.feign.dto.OrderAddResponseDto;
import com.codeterian.queue.presentation.dto.QueueResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final RedisTemplate<String, String> redisTemplate;

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final ObjectMapper objectMapper;

    private final OrderService orderService;

    // 대기열 임계치 설정
    private static final int TRAFFIC_THRESHOLD = 100; // 대기 없이 최대 100명까지 처리

    private static final String WAITING_QUEUE = "WaitingQueue";

    private static final String RUNNING_QUEUE = "RunningQueue";

    private static final String ORDER_REQUEST = "OrderRequest:";

    private static final String PASSPORT_KEY = "Passport:";

    /**
     * 사용자를 대기큐 or 실행큐에 추가, requestDto를 userId를 키로 해서 저장
     */
    public void joinQueue(OrderAddRequestDto requestDto, Passport passport) {
        Long currentRunningQueueSize = redisTemplate.opsForZSet().size(RUNNING_QUEUE);

        redisTemplate.opsForValue().set(ORDER_REQUEST + requestDto.userId().toString(), convertToJson(requestDto));
        redisTemplate.opsForValue().set(PASSPORT_KEY + requestDto.userId().toString(), convertPassportToJson(passport));

        if (currentRunningQueueSize != null && currentRunningQueueSize >= TRAFFIC_THRESHOLD) {
            //임계치 초과: 대기큐에 사용자 추가
            redisTemplate.opsForZSet().add(WAITING_QUEUE, requestDto.userId().toString(), System.currentTimeMillis());


            // 대기열에서의 순서 확인
            Long waitingPosition = redisTemplate.opsForZSet().rank(WAITING_QUEUE, requestDto.userId().toString());
            sendQueuePosition(requestDto.userId().toString(), waitingPosition);
        } else {
            //실행큐에 추가
            redisTemplate.opsForZSet().add(RUNNING_QUEUE, requestDto.userId().toString(), System.currentTimeMillis());

            log.info("redis에 저장되어 있는지 확인"+redisTemplate.opsForZSet().range(RUNNING_QUEUE, 0, 0));

            // 대기열에서의 순서 확인
            Long waitingPosition = redisTemplate.opsForZSet().rank(RUNNING_QUEUE, requestDto.userId().toString());
            sendQueuePosition(requestDto.userId().toString(), waitingPosition);
        }
    }

    /**
     * 대기큐에서 사용자를 꺼내서 실행큐로 이동
     * 스케줄러로 실행하여 대기큐에서의 대기 시간을 최소화
     */
    @Scheduled(fixedDelay = 5000)
    public void getNextUserFrom() {
        Set<String> nextUsers = redisTemplate.opsForZSet().range(WAITING_QUEUE, 0, 0);

        if (nextUsers != null && !nextUsers.isEmpty()) {
            String nextUser = nextUsers.iterator().next();
            redisTemplate.opsForZSet().remove(WAITING_QUEUE, nextUser);

            //실행큐로 이동
            redisTemplate.opsForZSet().add(RUNNING_QUEUE, nextUser, System.currentTimeMillis());

            // 대기열에서의 순서 확인
            Long waitingPosition = redisTemplate.opsForZSet().rank(RUNNING_QUEUE, nextUser);
            sendQueuePosition(nextUser, waitingPosition);

        }
    }

    public Long getQueuePosition(String userId) {
        Long position = redisTemplate.opsForZSet().rank(WAITING_QUEUE, userId);

        if (position != null) {
            return position + 1; // 대기큐에서의 순위 반환 (0 기반이므로 1을 더함)
        }

        // 대기큐에 없을 경우 실행큐에서 순위 확인
        position = redisTemplate.opsForZSet().rank(RUNNING_QUEUE, userId);

        if (position != null) {
            return position + 1; // 실행큐에서의 순위 반환
        }

        // 사용자 대기열과 실행큐 모두에서 순위를 찾지 못했을 때 예외 처리
        throw new IllegalStateException("사용자를 찾을 수 없습니다: " + userId);
    }


    /**
     * WebSocket으로 대기열 순위 전송
     */
    private void sendQueuePosition(String userId, Long position) {
        simpMessagingTemplate.convertAndSendToUser(userId,
                "/queue/position", new QueueResponseDto(position));
    }

    /**
     * 실패 알림을 사용자에게 전송하는 메서드
     */
    private void sendFailureNotification(String userId) {
        simpMessagingTemplate.convertAndSendToUser(
                userId, "/queue/errors", "대기열 접속은 성공했으나 주문 처리에 실패했습니다. 다시 시도해 주세요.");
    }

    @Scheduled(fixedDelay = 5000)
    public void processNextUserInRunningQueue() {
        Set<String> nextUsers = redisTemplate.opsForZSet().range(RUNNING_QUEUE, 0, 0);
        if (nextUsers != null && !nextUsers.isEmpty()) {
            String nextUser = nextUsers.iterator().next();

            //redis에서 해당 유저의 reqeustDto를 가져와야 됨.
            String requestJson = redisTemplate.opsForValue().get(ORDER_REQUEST + nextUser);
            String passportJson = redisTemplate.opsForValue().get(PASSPORT_KEY + nextUser);

            Passport passport = convertJsonToPassport(passportJson);
            PassportContextHolder.setPassport(passport); // PassportContext에 설정

            // 주문 처리
            processOrder(nextUser, convertFromJson(requestJson));
        }
    }

    /**
     * 주문 처리 로직
     */
    private void processOrder(String userId, OrderAddRequestDto requestDto) {
        OrderAddResponseDto responseDto = orderService.orderAdd(requestDto).getData();

        if (responseDto != null && responseDto.orderId() != null) {
            //처리된 사용자의 정보 삭제
            redisTemplate.opsForZSet().remove(RUNNING_QUEUE, userId);
            redisTemplate.delete(ORDER_REQUEST + userId);
            redisTemplate.delete(PASSPORT_KEY + userId);
        } else {
            sendFailureNotification(userId);
        }
    }

    /**
     * RequestDto를 Json 문자열로 변환, Json 문자열을 reqeustDto로 변환
     */
    private String convertToJson(OrderAddRequestDto requestDto) {
        try {
            return objectMapper.writeValueAsString(requestDto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert requestDto to JSON", e);
        }
    }

    private OrderAddRequestDto convertFromJson(String json) {
        try {
            return objectMapper.readValue(json, OrderAddRequestDto.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON to requestDto", e);
        }
    }

    /**
     * Passport를 Json 문자열로 변환, Json 문자열을 Passport로 변환
     */
    private String convertPassportToJson(Passport passport) {
        try {
            return objectMapper.writeValueAsString(passport);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert Passport to JSON", e);
        }
    }

    private Passport convertJsonToPassport(String json) {
        try {
            return objectMapper.readValue(json, Passport.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON to Passport", e);
        }
    }

}
