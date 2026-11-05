package com.omnitask.AuthAndProfiles.infrastructure.config;

import com.omnitask.AuthAndProfiles.domain.events.Topics;
import com.omnitask.AuthAndProfiles.domain.exceptions.NotFoundException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

/**
 * Topología RabbitMQ de Authentication & Profile.
 * <ul>
 * <li>Un exchange <b>topic</b> compartido por todos los microservicios (app.rabbit.exchange). La routing key de cada
 * mensaje es el nombre lógico que antes era el topic de Kafka (taskit.auth.events, taskit.security.events...).</li>
 * <li>Este MS tiene su propia cola por cada tipo de evento que consume, ligada a esa routing key.</li>
 * <li>Los mensajes que fallan tras los reintentos van, por dead-lettering de RabbitMQ, a la cola taskit.auth.dead-letter.</li>
 * </ul>
 * Los nombres se pueden cambiar por variables de entorno para ajustarse a las reglas que acuerde el equipo.
 */
@Configuration
@EnableScheduling
public class RabbitConfig {

    /** Intentos totales por mensaje: el primero más 3 reintentos (igual que el DefaultErrorHandler que había con Kafka). */
    static final int MAX_ATTEMPTS = 4;
    static final long RETRY_BACKOFF_MS = 1000L;

    @Bean
    public TopicExchange eventsExchange(@Value("${app.rabbit.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange(@Value("${app.rabbit.dead-letter-exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(Topics.DEAD_LETTER).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(Topics.DEAD_LETTER);
    }

    @Bean
    public Queue securityEventsQueue(@Value("${app.rabbit.security-queue}") String name,
                                     @Value("${app.rabbit.dead-letter-exchange}") String deadLetterExchange) {
        return withDeadLetter(name, deadLetterExchange);
    }

    @Bean
    public Binding securityEventsBinding(Queue securityEventsQueue, TopicExchange eventsExchange,
                                         @Value("${app.rabbit.security-routing-key}") String routingKey) {
        return BindingBuilder.bind(securityEventsQueue).to(eventsExchange).with(routingKey);
    }

    @Bean
    public Queue taskEventsQueue(@Value("${app.rabbit.task-queue}") String name,
                                 @Value("${app.rabbit.dead-letter-exchange}") String deadLetterExchange) {
        return withDeadLetter(name, deadLetterExchange);
    }

    @Bean
    public Binding taskEventsBinding(Queue taskEventsQueue, TopicExchange eventsExchange,
                                     @Value("${app.rabbit.task-routing-key}") String routingKey) {
        return BindingBuilder.bind(taskEventsQueue).to(eventsExchange).with(routingKey);
    }

    private static Queue withDeadLetter(String name, String deadLetterExchange) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(Topics.DEAD_LETTER)
                .build();
    }

    /**
     * Manejo de errores de los consumidores: 3 reintentos con 1 s de espera; luego el mensaje se rechaza sin
     * reencolar y RabbitMQ lo manda a la cola de dead letter. Los errores de datos (evento inválido, usuario
     * inexistente) no se reintentan porque nunca se arreglan solos.
     */
    @Bean
    public RetryOperationsInterceptor listenerRetryInterceptor() {
        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(IllegalArgumentException.class, false);
        retryable.put(NotFoundException.class, false);
        // traverseCauses = true porque Spring AMQP envuelve la excepción del listener en ListenerExecutionFailedException;
        // defaultValue = true: todo lo demás (Mongo caído, errores transitorios) sí se reintenta.
        SimpleRetryPolicy policy = new SimpleRetryPolicy(MAX_ATTEMPTS, retryable, true, true);

        MessageRecoverer recoverer = new RejectAndDontRequeueRecoverer();
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(policy)
                .backOffOptions(RETRY_BACKOFF_MS, 1.0, RETRY_BACKOFF_MS)
                .recoverer(recoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            RetryOperationsInterceptor listenerRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(listenerRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
