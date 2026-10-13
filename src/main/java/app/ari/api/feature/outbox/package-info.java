/**
 * Background jobs through a transactional outbox: a feature adds an event in the same transaction
 * as its own change, and the publisher sends it to RabbitMQ after the commit.
 */
package app.ari.api.feature.outbox;
