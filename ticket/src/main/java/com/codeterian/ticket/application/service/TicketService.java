package com.codeterian.ticket.application.service;

import com.codeterian.ticket.domain.model.Ticket;
import com.codeterian.ticket.domain.repository.TicketRepository;
import com.codeterian.ticket.presentation.dto.request.TicketAddRequestDto;
import com.codeterian.ticket.presentation.dto.request.TicketModifyRequestDto;
import com.codeterian.ticket.presentation.dto.response.TicketFindResponseDto;
import com.codeterian.ticket.presentation.dto.response.TicketModifyResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;

    @Transactional
    public void addTicket(TicketAddRequestDto requestDto) {
        Ticket ticket = Ticket.create(requestDto.performanceId(), requestDto.ticketStatus(), requestDto.price(),
                requestDto.seatSection(), requestDto.seatNumber(), UUID.randomUUID());

        ticketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public TicketFindResponseDto findTicketById(UUID ticketId) {

        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId).orElseThrow(
                //Global Exception Handler
        );
        return TicketFindResponseDto.fromEntity(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketFindResponseDto> findAllTicket() {
        return ticketRepository.findAllByDeletedAtIsNull().stream()
                .map(TicketFindResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public TicketModifyResponseDto modifyTicket(UUID ticketId, TicketModifyRequestDto requestDto) {

        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId).orElseThrow(
                //Global Exception Handler
        );

        ticket.update(requestDto.seatNumber(),requestDto.seatSection(),
                requestDto.ticketStatus(), requestDto.price());

        return TicketModifyResponseDto.fromEntity(ticket);
    }

    @Transactional(readOnly = true)
    public void deleteTicketById(UUID ticketId) {

        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId).orElseThrow(
                //Global Exception Handler
        );

//        ticket.delete(userId);
        ticket.delete(1L);

    }

}