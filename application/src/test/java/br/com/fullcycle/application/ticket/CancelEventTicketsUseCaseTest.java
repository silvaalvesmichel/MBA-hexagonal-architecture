package br.com.fullcycle.application.ticket;

import br.com.fullcycle.application.repository.InMemoryTicketRepository;
import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEventTicketsUseCaseTest {

    @Test
    @DisplayName("Deve cancelar todos os tickets de um evento")
    public void testCancelTickets() throws Exception {
        // given
        final var expectedCancelledTickets = 2;

        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);
        final var anotherEvent = Event.newEvent("Disney on Fire", "2021-01-02", 10, aPartner);
        final var aCustomer = Customer.newCustomer("Gabriel Doe", "123.456.789-01", "gabriel.doe@gmail.com");
        final var aCustomer2 = Customer.newCustomer("Pedro Doe", "123.111.789-01", "pedro.doe@gmail.com");

        final var aTicket = Ticket.newTicket(aCustomer.customerId(), anEvent.eventId());
        final var aTicket2 = Ticket.newTicket(aCustomer2.customerId(), anEvent.eventId());
        final var ticketOfAnotherEvent = Ticket.newTicket(aCustomer.customerId(), anotherEvent.eventId());

        final var ticketRepository = new InMemoryTicketRepository();
        ticketRepository.create(aTicket);
        ticketRepository.create(aTicket2);
        ticketRepository.create(ticketOfAnotherEvent);

        final var expectedEventID = anEvent.eventId().value();

        final var input = new CancelEventTicketsUseCase.Input(expectedEventID);

        // when
        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(expectedEventID, output.eventId());
        Assertions.assertEquals(expectedCancelledTickets, output.cancelledTickets());

        final var actualTickets = ticketRepository.ticketsByEventId(anEvent.eventId());
        Assertions.assertEquals(expectedCancelledTickets, actualTickets.size());
        Assertions.assertTrue(actualTickets.stream().allMatch(it -> TicketStatus.CANCELLED == it.status()));

        final var actualTicketOfAnotherEvent = ticketRepository.ticketOfId(ticketOfAnotherEvent.ticketId()).get();
        Assertions.assertEquals(TicketStatus.PENDING, actualTicketOfAnotherEvent.status());
    }

    @Test
    @DisplayName("Reprocessar o cancelamento dos tickets de um evento deve ser idempotente")
    public void testCancelTicketsTwice() throws Exception {
        // given
        final var expectedCancelledTickets = 1;

        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);
        final var aCustomer = Customer.newCustomer("Gabriel Doe", "123.456.789-01", "gabriel.doe@gmail.com");

        final var aTicket = Ticket.newTicket(aCustomer.customerId(), anEvent.eventId());

        final var ticketRepository = new InMemoryTicketRepository();
        ticketRepository.create(aTicket);

        final var input = new CancelEventTicketsUseCase.Input(anEvent.eventId().value());

        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        useCase.execute(input);

        // when
        final var output = Assertions.assertDoesNotThrow(() -> useCase.execute(input));

        // then
        Assertions.assertEquals(expectedCancelledTickets, output.cancelledTickets());

        final var actualTicket = ticketRepository.ticketOfId(aTicket.ticketId()).get();
        Assertions.assertEquals(TicketStatus.CANCELLED, actualTicket.status());
    }

    @Test
    @DisplayName("Não deve falhar ao cancelar os tickets de um evento sem tickets")
    public void testCancelTicketsWithoutTickets() throws Exception {
        // given
        final var expectedCancelledTickets = 0;
        final var expectedEventID = EventId.unique().value();

        final var input = new CancelEventTicketsUseCase.Input(expectedEventID);

        final var ticketRepository = new InMemoryTicketRepository();

        // when
        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(expectedEventID, output.eventId());
        Assertions.assertEquals(expectedCancelledTickets, output.cancelledTickets());
    }
}
