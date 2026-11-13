package br.com.fullcycle.infrastructure.graphql;

import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.infrastructure.jpa.repositories.OutboxJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@AutoConfigureGraphQlTester
@SpringBootTest
class EventResolverTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    private Partner disney;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        partnerRepository.deleteAll();

        disney = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
    }

    @AfterEach
    void tearDown() {
        outboxJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve continuar criando um evento via GraphQL")
    public void testCreateEvent() {
        final var document = """
                mutation {
                    createEvent(input: {name: "Disney on Ice", date: "2021-01-01", totalSpots: 100, partnerId: "%s"}) {
                        id
                        name
                        date
                        totalSpots
                    }
                }
                """.formatted(disney.partnerId().value());

        this.graphQlTester.document(document)
                .execute()
                .path("createEvent.id").hasValue()
                .path("createEvent.name").entity(String.class).isEqualTo("Disney on Ice")
                .path("createEvent.date").entity(String.class).isEqualTo("2021-01-01")
                .path("createEvent.totalSpots").entity(Integer.class).isEqualTo(100);
    }

    @Test
    @DisplayName("Deve obter um evento por id com o seu estado via GraphQL")
    public void testEventOfId() {
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));

        final var document = """
                query {
                    eventOfId(id: "%s") {
                        id
                        name
                        date
                        totalSpots
                        status
                    }
                }
                """.formatted(anEvent.eventId().value());

        this.graphQlTester.document(document)
                .execute()
                .path("eventOfId.id").entity(String.class).isEqualTo(anEvent.eventId().value())
                .path("eventOfId.name").entity(String.class).isEqualTo("Disney on Ice")
                .path("eventOfId.date").entity(String.class).isEqualTo("2021-01-01")
                .path("eventOfId.totalSpots").entity(Integer.class).isEqualTo(100)
                .path("eventOfId.status").entity(String.class).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Deve obter nulo ao consultar um evento inexistente via GraphQL")
    public void testEventOfIdNotFound() {
        final var document = """
                query {
                    eventOfId(id: "%s") {
                        id
                        status
                    }
                }
                """.formatted(EventId.unique().value());

        this.graphQlTester.document(document)
                .execute()
                .path("eventOfId").valueIsNull();
    }

    @Test
    @DisplayName("Deve cancelar um evento via GraphQL")
    public void testCancelEvent() {
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));

        final var document = """
                mutation {
                    cancelEvent(id: "%s") {
                        id
                        status
                    }
                }
                """.formatted(anEvent.eventId().value());

        this.graphQlTester.document(document)
                .execute()
                .path("cancelEvent.id").entity(String.class).isEqualTo(anEvent.eventId().value())
                .path("cancelEvent.status").entity(String.class).isEqualTo("CANCELLED");

        final var actualEvent = eventRepository.eventOfId(anEvent.eventId()).get();
        Assertions.assertTrue(actualEvent.isCancelled());
    }

    @Test
    @DisplayName("Não deve cancelar um evento já cancelado via GraphQL")
    public void testCancelEventTwice() {
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 100, disney);
        anEvent.cancel();
        eventRepository.create(anEvent);

        final var document = """
                mutation {
                    cancelEvent(id: "%s") {
                        id
                        status
                    }
                }
                """.formatted(anEvent.eventId().value());

        this.graphQlTester.document(document)
                .execute()
                .errors()
                .satisfy(errors -> Assertions.assertFalse(errors.isEmpty()));
    }
}
