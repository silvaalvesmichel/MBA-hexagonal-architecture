package br.com.fullcycle.application.usecases;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.application.event.CancelEventUseCase;
import br.com.fullcycle.application.event.GetEventByIdUseCase;
import br.com.fullcycle.application.event.SubscribeCustomerToEventUseCase;
import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.customer.CustomerRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.exceptions.ValidationException;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.infrastructure.jpa.repositories.OutboxJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.StreamSupport;

class CancelEventUseCaseIT extends IntegrationTest {

    @Autowired
    private CancelEventUseCase useCase;

    @Autowired
    private GetEventByIdUseCase getEventByIdUseCase;

    @Autowired
    private SubscribeCustomerToEventUseCase subscribeCustomerToEventUseCase;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    private Partner disney;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        customerRepository.deleteAll();
        partnerRepository.deleteAll();

        disney = partnerRepository.create(Partner.newPartner("Disney", "41.536.538/0001-00", "disney@gmail.com"));
    }

    @AfterEach
    void tearDown() {
        outboxJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve cancelar um evento e persistir o novo estado")
    public void testCancel() throws Exception {
        // given
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, disney));

        final var expectedID = anEvent.eventId().value();
        final var expectedStatus = "CANCELLED";

        final var input = new CancelEventUseCase.Input(expectedID);

        // when
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(expectedID, output.id());
        Assertions.assertEquals(expectedStatus, output.status());

        final var actualEvent = eventRepository.eventOfId(EventId.with(expectedID)).get();
        Assertions.assertEquals(EventStatus.CANCELLED, actualEvent.status());

        final var actualOutput = getEventByIdUseCase.execute(new GetEventByIdUseCase.Input(expectedID)).get();
        Assertions.assertEquals(expectedStatus, actualOutput.status());
    }

    @Test
    @DisplayName("Deve gravar o evento de domínio EventCancelled na outbox ao cancelar um evento")
    public void testCancelShouldWriteOutbox() throws Exception {
        // given
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, disney));

        final var expectedID = anEvent.eventId().value();
        final var expectedOutboxEvents = 1;

        final var input = new CancelEventUseCase.Input(expectedID);

        // when
        useCase.execute(input);

        // then
        final var actualOutboxEvents = StreamSupport.stream(outboxJpaRepository.findAll().spliterator(), false)
                .filter(it -> it.content().contains("event.cancelled"))
                .filter(it -> it.content().contains(expectedID))
                .count();

        Assertions.assertEquals(expectedOutboxEvents, actualOutboxEvents);
    }

    @Test
    @DisplayName("Deve persistir um evento recém criado como ativo")
    public void testNewEventIsActive() throws Exception {
        // given
        final var expectedStatus = "ACTIVE";

        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, disney));

        // when
        final var actualOutput =
                getEventByIdUseCase.execute(new GetEventByIdUseCase.Input(anEvent.eventId().value())).get();

        // then
        Assertions.assertEquals(expectedStatus, actualOutput.status());
    }

    @Test
    @DisplayName("Não deve cancelar um evento que não existe")
    public void testCancelWithoutEvent() throws Exception {
        // given
        final var expectedError = "Event not found";

        final var input = new CancelEventUseCase.Input(EventId.unique().value());

        // when
        final var actualException = Assertions.assertThrows(ValidationException.class, () -> useCase.execute(input));

        // then
        Assertions.assertEquals(expectedError, actualException.getMessage());
    }

    @Test
    @DisplayName("Não deve cancelar um evento já cancelado")
    public void testCancelTwice() throws Exception {
        // given
        final var expectedError = "Event already cancelled";

        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, disney));

        final var input = new CancelEventUseCase.Input(anEvent.eventId().value());

        useCase.execute(input);

        // when
        final var actualException = Assertions.assertThrows(ValidationException.class, () -> useCase.execute(input));

        // then
        Assertions.assertEquals(expectedError, actualException.getMessage());
    }

    @Test
    @DisplayName("Não deve inscrever um cliente em um evento cancelado")
    public void testSubscribeToCancelledEvent() throws Exception {
        // given
        final var expectedError = "Event is cancelled";

        final var aCustomer =
                customerRepository.create(Customer.newCustomer("John Doe", "123.456.789-01", "john.doe@gmail.com"));
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, disney));

        useCase.execute(new CancelEventUseCase.Input(anEvent.eventId().value()));

        final var subscribeInput =
                new SubscribeCustomerToEventUseCase.Input(aCustomer.customerId().value(), anEvent.eventId().value());

        // when
        final var actualException =
                Assertions.assertThrows(ValidationException.class, () -> subscribeCustomerToEventUseCase.execute(subscribeInput));

        // then
        Assertions.assertEquals(expectedError, actualException.getMessage());
    }
}
