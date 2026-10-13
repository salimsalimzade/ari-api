package app.ari.api.feature.mail.application.facade.impl;

import app.ari.api.feature.mail.application.facade.MailFacade;
import app.ari.api.feature.mail.application.model.message.EmailJob;
import app.ari.api.feature.mail.domain.service.MailService;
import org.springframework.stereotype.Service;

@Service
public class MailFacadeImpl implements MailFacade {

  private final MailService mailService;

  public MailFacadeImpl(MailService mailService) {
    this.mailService = mailService;
  }

  @Override
  public boolean deliver(EmailJob job) {
    return mailService.deliver(job.eventId(), job.type(), job.data());
  }
}
