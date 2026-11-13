package br.com.fullcycle.infrastructure.repositories;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

class TicketDatabaseRepositoryIT extends IntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve buscar apenas os tickets do evento informado")
    public void testTicketsByEventId() {
        // given
        final var expectedEventId = EventId.unique();
        final var anotherEventId = EventId.unique();

        final var aTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), expectedEventId));
        final var aTicket2 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), expectedEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anotherEventId));

        final var expectedTicketIds = Set.of(aTicket.ticketId(), aTicket2.ticketId());

        // when
        final var actualTickets = ticketRepository.ticketsByEventId(expectedEventId);

        // then
        Assertions.assertEquals(expectedTicketIds.size(), actualTickets.size());
        Assertions.assertEquals(expectedTicketIds, actualTickets.stream().map(Ticket::ticketId).collect(Collectors.toSet()));
        Assertions.assertTrue(actualTickets.stream().allMatch(it -> expectedEventId.equals(it.eventId())));
    }

    @Test
    @DisplayName("Deve retornar vazio quando o evento não possui tickets")
    public void testTicketsByEventIdWithoutTickets() {
        // given
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), EventId.unique()));

        // when
        final var actualTickets = ticketRepository.ticketsByEventId(EventId.unique());

        // then
        Assertions.assertTrue(actualTickets.isEmpty());
    }

    @Test
    @DisplayName("Deve persistir o cancelamento de um ticket")
    public void testUpdateCancelledTicket() {
        // given
        final var expectedStatus = TicketStatus.CANCELLED;

        final var aTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), EventId.unique()));
        aTicket.cancel();

        // when
        ticketRepository.update(aTicket);

        // then
        final var actualTicket = ticketRepository.ticketOfId(aTicket.ticketId()).get();
        Assertions.assertEquals(expectedStatus, actualTicket.status());
    }
}
