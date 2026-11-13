package br.com.fullcycle.infrastructure.gateways;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventCancelled;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.infrastructure.jpa.repositories.OutboxJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// End-to-end proof of the asynchronous cascade: EventCancelled -> queue -> CancelEventTicketsUseCase
class EventCancelledFlowIT extends IntegrationTest {

    @Autowired
    private QueueGateway queueGateway;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        partnerRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        outboxJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve cancelar os tickets do evento ao consumir o EventCancelled publicado na fila")
    public void testEventCancelledPublishedOnQueueShouldCancelTickets() throws Exception {
        // given
        final var expectedEventId = EventId.unique();
        final var anotherEventId = EventId.unique();
        final var expectedTickets = 2;

        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), expectedEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), expectedEventId));
        final var ticketOfAnotherEvent = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anotherEventId));

        final var content = mapper.writeValueAsString(new EventCancelled(expectedEventId));

        // when
        queueGateway.publish(content);

        // then
        final var actualTickets = ticketRepository.ticketsByEventId(expectedEventId);
        Assertions.assertEquals(expectedTickets, actualTickets.size());
        Assertions.assertTrue(actualTickets.stream().allMatch(it -> TicketStatus.CANCELLED == it.status()));

        final var actualTicketOfAnotherEvent = ticketRepository.ticketOfId(ticketOfAnotherEvent.ticketId()).get();
        Assertions.assertEquals(TicketStatus.PENDING, actualTicketOfAnotherEvent.status());
    }

    @Test
    @DisplayName("Reprocessar o mesmo EventCancelled na fila deve ser idempotente")
    public void testEventCancelledReprocessedShouldBeIdempotent() throws Exception {
        // given
        final var expectedEventId = EventId.unique();
        final var expectedTickets = 2;

        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), expectedEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), expectedEventId));

        final var content = mapper.writeValueAsString(new EventCancelled(expectedEventId));

        queueGateway.publish(content);

        // when
        Assertions.assertDoesNotThrow(() -> queueGateway.publish(content));

        // then
        final var actualTickets = ticketRepository.ticketsByEventId(expectedEventId);
        Assertions.assertEquals(expectedTickets, actualTickets.size());
        Assertions.assertTrue(actualTickets.stream().allMatch(it -> TicketStatus.CANCELLED == it.status()));
    }

    @Test
    @DisplayName("Deve cancelar os tickets ao publicar na fila o evento de domínio registrado pelo cancelamento do agregado")
    public void testDomainEventOfCancelledAggregateShouldCancelTickets() throws Exception {
        // given
        final var aPartner =
                partnerRepository.create(Partner.newPartner("Disney", "41.536.538/0001-00", "disney@gmail.com"));
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner));

        final var expectedEventId = anEvent.eventId();
        final var expectedTickets = 2;

        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), expectedEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), expectedEventId));

        anEvent.cancel();
        eventRepository.update(anEvent);

        final var content = mapper.writeValueAsString(anEvent.allDomainEvents().iterator().next());

        // when
        queueGateway.publish(content);

        // then
        final var actualTickets = ticketRepository.ticketsByEventId(expectedEventId);
        Assertions.assertEquals(expectedTickets, actualTickets.size());
        Assertions.assertTrue(actualTickets.stream().allMatch(it -> TicketStatus.CANCELLED == it.status()));

        Assertions.assertTrue(eventRepository.eventOfId(expectedEventId).get().isCancelled());
    }
}
