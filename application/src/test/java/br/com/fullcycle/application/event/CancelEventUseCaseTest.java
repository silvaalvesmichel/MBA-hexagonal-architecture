package br.com.fullcycle.application.event;

import br.com.fullcycle.application.repository.InMemoryEventRepository;
import br.com.fullcycle.application.repository.InMemoryTicketRepository;
import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventCancelled;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import br.com.fullcycle.domain.exceptions.ValidationException;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEventUseCaseTest {

    @Test
    @DisplayName("Deve cancelar um evento ativo")
    public void testCancel() throws Exception {
        // given
        final var expectedStatus = "CANCELLED";
        final var expectedDomainEvent = "event.cancelled";

        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);

        final var expectedID = anEvent.eventId().value();

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var input = new CancelEventUseCase.Input(expectedID);

        // when
        final var useCase = new CancelEventUseCase(eventRepository);
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(expectedID, output.id());
        Assertions.assertEquals(expectedStatus, output.status());

        final var actualEvent = eventRepository.eventOfId(anEvent.eventId()).get();
        Assertions.assertTrue(actualEvent.isCancelled());

        final var actualDomainEvent = actualEvent.allDomainEvents().iterator().next();
        Assertions.assertInstanceOf(EventCancelled.class, actualDomainEvent);
        Assertions.assertEquals(expectedDomainEvent, actualDomainEvent.type());
    }

    @Test
    @DisplayName("Não deve cancelar os tickets de forma síncrona ao cancelar o evento")
    public void testCancelDoesNotTouchTickets() throws Exception {
        // given
        final var expectedTicketStatus = TicketStatus.PENDING;

        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var aCustomer = Customer.newCustomer("Gabriel Doe", "123.456.789-01", "gabriel.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);
        final var aTicket = Ticket.newTicket(aCustomer.customerId(), anEvent.eventId());

        final var eventRepository = new InMemoryEventRepository();
        final var ticketRepository = new InMemoryTicketRepository();

        eventRepository.create(anEvent);
        ticketRepository.create(aTicket);

        final var input = new CancelEventUseCase.Input(anEvent.eventId().value());

        // when
        final var useCase = new CancelEventUseCase(eventRepository);
        useCase.execute(input);

        // then
        final var actualTicket = ticketRepository.ticketOfId(aTicket.ticketId()).get();
        Assertions.assertEquals(expectedTicketStatus, actualTicket.status());
    }

    @Test
    @DisplayName("Não deve cancelar um evento que não existe")
    public void testCancelWithoutEvent() throws Exception {
        // given
        final var expectedError = "Event not found";

        final var input = new CancelEventUseCase.Input(EventId.unique().value());

        final var eventRepository = new InMemoryEventRepository();

        // when
        final var useCase = new CancelEventUseCase(eventRepository);
        final var actualException = Assertions.assertThrows(ValidationException.class, () -> useCase.execute(input));

        // then
        Assertions.assertEquals(expectedError, actualException.getMessage());
    }

    @Test
    @DisplayName("Não deve cancelar um evento já cancelado")
    public void testCancelTwice() throws Exception {
        // given
        final var expectedError = "Event already cancelled";

        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var input = new CancelEventUseCase.Input(anEvent.eventId().value());

        final var useCase = new CancelEventUseCase(eventRepository);
        useCase.execute(input);

        // when
        final var actualException = Assertions.assertThrows(ValidationException.class, () -> useCase.execute(input));

        // then
        Assertions.assertEquals(expectedError, actualException.getMessage());
    }
}
