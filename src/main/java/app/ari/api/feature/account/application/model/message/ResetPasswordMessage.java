package app.ari.api.feature.account.application.model.message;

/**
 * The {@code ResetPassword} job on {@code email.transactional}: the mail sender turns it into an
 * email.
 *
 * @param to the account's verified email
 * @param fullName for the greeting
 * @param username the account the link is for, as people sign in with it
 * @param language {@code en} or {@code az}, from the {@code ari_lang} cookie
 * @param link the website's reset page with the code
 */
public record ResetPasswordMessage(
    String to, String fullName, String username, String language, String link) {}
