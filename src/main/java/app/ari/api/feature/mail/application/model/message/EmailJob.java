package app.ari.api.feature.mail.application.model.message;

import java.util.Map;

/**
 * An email job from RabbitMQ.
 *
 * @param eventId the outbox id, so a repeated delivery is sent only once
 * @param type e.g. {@code VerifyEmail}; picks the template
 * @param data the JSON body: {@code to}, {@code language} and the template's values
 */
public record EmailJob(String eventId, String type, Map<String, Object> data) {}
