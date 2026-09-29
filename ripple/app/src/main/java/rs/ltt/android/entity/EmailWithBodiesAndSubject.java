package rs.ltt.android.entity;

import rs.ltt.android.mail.model.IdentifiableEmailWithSubject;

/**
 * This e-mail model is used to display notifications. While e-mails in thread view don’t each need
 * their own subject (the subject is loaded once for all e-mails), e-mails in the notification are
 * displayed individually and thus need their own subject
 */
public class EmailWithBodiesAndSubject extends EmailWithBodies
        implements IdentifiableEmailWithSubject {

    public String subject;

    @Override
    public String getSubject() {
        return subject;
    }
}
