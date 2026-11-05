package com.omnitask.AuthAndProfiles.messaging;

import com.omnitask.AuthAndProfiles.domain.events.Topics;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import com.omnitask.AuthAndProfiles.infrastructure.config.RabbitConfig;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void exchanges_deberianSerDuraderos_yElDeEventosDeTipoTopic() {
        TopicExchange events = config.eventsExchange("taskit.events");
        DirectExchange dlx = config.deadLetterExchange("taskit.dlx");

        assertThat(events.getName()).isEqualTo("taskit.events");
        assertThat(events.isDurable()).isTrue();
        assertThat(dlx.getName()).isEqualTo("taskit.dlx");
        assertThat(dlx.isDurable()).isTrue();
    }

    @Test
    void colasDeConsumo_deberianEnviarSusFallosAlDeadLetterExchange() {
        Queue security = config.securityEventsQueue("auth-profile.security-events", "taskit.dlx");
        Queue task = config.taskEventsQueue("auth-profile.task-events", "taskit.dlx");

        for (Queue queue : new Queue[]{security, task}) {
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.getArguments())
                    .containsEntry("x-dead-letter-exchange", "taskit.dlx")
                    .containsEntry("x-dead-letter-routing-key", Topics.DEAD_LETTER);
        }
    }

    @Test
    void bindings_deberianLigarCadaColaASuRoutingKeyEnElExchangeDeEventos() {
        TopicExchange events = config.eventsExchange("taskit.events");
        Queue security = config.securityEventsQueue("auth-profile.security-events", "taskit.dlx");
        Queue task = config.taskEventsQueue("auth-profile.task-events", "taskit.dlx");

        Binding securityBinding = config.securityEventsBinding(security, events, Topics.SECURITY_EVENTS);
        Binding taskBinding = config.taskEventsBinding(task, events, Topics.TASK_EVENTS);

        assertThat(securityBinding.getExchange()).isEqualTo("taskit.events");
        assertThat(securityBinding.getDestination()).isEqualTo("auth-profile.security-events");
        assertThat(securityBinding.getRoutingKey()).isEqualTo("taskit.security.events");
        assertThat(taskBinding.getDestination()).isEqualTo("auth-profile.task-events");
        assertThat(taskBinding.getRoutingKey()).isEqualTo("taskit.task.events");
    }

    @Test
    void deadLetterQueue_deberiaLigarseAlDeadLetterExchange() {
        DirectExchange dlx = config.deadLetterExchange("taskit.dlx");

        Binding binding = config.deadLetterBinding(config.deadLetterQueue(), dlx);

        assertThat(binding.getDestination()).isEqualTo("taskit.auth.dead-letter");
        assertThat(binding.getExchange()).isEqualTo("taskit.dlx");
        assertThat(binding.getRoutingKey()).isEqualTo("taskit.auth.dead-letter");
    }

    // ------------------------------------------------------------ reintentos

    private MethodInvocation invocationQueFalla(Throwable causa, AtomicInteger llamadas) throws Throwable {
        MethodInvocation invocation = mock(MethodInvocation.class);
        Message message = new Message(new byte[0], new MessageProperties());
        // El interceptor de reintentos usa el método invocado como etiqueta de la operación
        when(invocation.getMethod()).thenReturn(Object.class.getMethod("toString"));
        when(invocation.getArguments()).thenReturn(new Object[]{null, message});
        when(invocation.proceed()).thenAnswer(call -> {
            llamadas.incrementAndGet();
            throw new ListenerExecutionFailedException("falló el listener", causa, message);
        });
        return invocation;
    }

    @Test
    void reintentos_noDeberianReintentarLosErroresDeDatos_yDebenIrAlDeadLetter() throws Throwable {
        RetryOperationsInterceptor interceptor = config.listenerRetryInterceptor();

        for (Throwable causa : new Throwable[]{new IllegalArgumentException("evento inválido"),
                new NotFoundException("usuario inexistente")}) {
            AtomicInteger llamadas = new AtomicInteger();
            MethodInvocation invocation = invocationQueFalla(causa, llamadas);

            assertThatThrownBy(() -> interceptor.invoke(invocation))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
            assertThat(llamadas.get()).as("intentos para %s", causa.getClass().getSimpleName()).isEqualTo(1);
        }
    }

    @Test
    void reintentos_deberianReintentarLosErroresTransitorios3VecesAntesDelDeadLetter() throws Throwable {
        RetryOperationsInterceptor interceptor = config.listenerRetryInterceptor();
        AtomicInteger llamadas = new AtomicInteger();
        MethodInvocation invocation = invocationQueFalla(new RuntimeException("mongo caído"), llamadas);

        assertThatThrownBy(() -> interceptor.invoke(invocation))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(llamadas.get()).isEqualTo(4); // el intento inicial más 3 reintentos
    }
}
