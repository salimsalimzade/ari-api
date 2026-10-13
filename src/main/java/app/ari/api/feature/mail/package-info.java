/**
 * The mail sender: takes email jobs off RabbitMQ, renders their template in the user's language and
 * sends them over SMTP. It reads no table and talks to the other features only through RabbitMQ, so
 * it can move to its own app unchanged.
 */
package app.ari.api.feature.mail;
