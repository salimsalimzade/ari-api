package app.ari.api.feature.account.application.model.message;

/**
 * The {@code VerifyEmail} job on {@code email.transactional}: the mail sender turns it into an
 * email.
 *
 * @param to the pending email
 * @param fullName for the greeting
 * @param language {@code en} or {@code az}, from the {@code ari_lang} cookie
 * @param link the website's verify page with the code
 */
public record VerifyEmailMessage(String to, String fullName, String language, String link) {}
