/*
 * Copyright 2019 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rs.ltt.android.ui.model;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.CertificateValidationException;
import com.fsck.k9.mail.oauth.OAuth2TokenProvider;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.LttrsApplication;
import rs.ltt.android.R;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.engine.Autoconfig;
import rs.ltt.android.engine.MailSettings;
import rs.ltt.android.engine.Mua;
import rs.ltt.android.engine.OAuthProvider;
import rs.ltt.android.engine.StaticTokenProvider;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.CredentialsEntity;
import rs.ltt.android.mail.util.EmailAddressUtil;
import rs.ltt.android.repository.MainRepository;
import rs.ltt.android.util.Event;

/**
 * Drives account setup: email address, then server discovery, then either OAuth (Google,
 * Microsoft) or a password, then a login test against IMAP/POP3 and SMTP.
 */
public class SetupViewModel extends AndroidViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetupViewModel.class);

    private static final ListeningExecutorService NETWORK =
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());

    public static final String[] SECURITY_OPTIONS = {"SSL_TLS_REQUIRED", "STARTTLS_REQUIRED", "NONE"};

    private final MutableLiveData<String> emailAddress = new MutableLiveData<>();
    private final MutableLiveData<String> emailAddressError = new MutableLiveData<>();
    private final MutableLiveData<String> password = new MutableLiveData<>();
    private final MutableLiveData<String> passwordError = new MutableLiveData<>();
    private final MutableLiveData<String> passwordHint = new MutableLiveData<>();
    private final MutableLiveData<String> passwordExplanation = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Target>> redirection = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> warningMessage = new MutableLiveData<>();
    private final MutableLiveData<Event<OAuthProvider>> oauthRequest = new MutableLiveData<>();

    // manual server settings
    private final MutableLiveData<Boolean> pop3 = new MutableLiveData<>(false);
    private final MutableLiveData<String> incomingHost = new MutableLiveData<>();
    private final MutableLiveData<String> incomingPort = new MutableLiveData<>();
    private final MutableLiveData<String> incomingSecurity = new MutableLiveData<>();
    private final MutableLiveData<String> smtpHost = new MutableLiveData<>();
    private final MutableLiveData<String> smtpPort = new MutableLiveData<>();
    private final MutableLiveData<String> smtpSecurity = new MutableLiveData<>();
    private final MutableLiveData<String> username = new MutableLiveData<>();
    private final MutableLiveData<String> serverSettingsError = new MutableLiveData<>();

    private final MainRepository mainRepository;
    private ListenableFuture<?> networkFuture = null;
    private Long primaryAccountId = null;

    public SetupViewModel(@NonNull Application application) {
        super(application);
        this.mainRepository = new MainRepository(application);
        this.passwordHint.setValue(application.getString(R.string.password));
        Transformations.distinctUntilChanged(emailAddress)
                .observeForever(s -> emailAddressError.postValue(null));
        Transformations.distinctUntilChanged(password)
                .observeForever(s -> passwordError.postValue(null));
    }

    // ------------------------------------------------------------------ getters

    public LiveData<Boolean> isLoading() {
        return this.loading;
    }

    public MutableLiveData<String> getEmailAddress() {
        return emailAddress;
    }

    public LiveData<String> getEmailAddressError() {
        return Transformations.distinctUntilChanged(emailAddressError);
    }

    public MutableLiveData<String> getPassword() {
        return password;
    }

    public LiveData<String> getPasswordError() {
        return Transformations.distinctUntilChanged(this.passwordError);
    }

    public LiveData<String> getPasswordHint() {
        return this.passwordHint;
    }

    public LiveData<String> getPasswordExplanation() {
        return this.passwordExplanation;
    }

    public MutableLiveData<Boolean> getPop3() {
        return pop3;
    }

    public MutableLiveData<String> getIncomingHost() {
        return incomingHost;
    }

    public MutableLiveData<String> getIncomingPort() {
        return incomingPort;
    }

    public MutableLiveData<String> getIncomingSecurity() {
        return incomingSecurity;
    }

    public MutableLiveData<String> getSmtpHost() {
        return smtpHost;
    }

    public MutableLiveData<String> getSmtpPort() {
        return smtpPort;
    }

    public MutableLiveData<String> getSmtpSecurity() {
        return smtpSecurity;
    }

    public MutableLiveData<String> getUsername() {
        return username;
    }

    public LiveData<String> getServerSettingsError() {
        return serverSettingsError;
    }

    public LiveData<Event<Target>> getRedirection() {
        return this.redirection;
    }

    public LiveData<Event<String>> getWarningMessage() {
        return this.warningMessage;
    }

    public LiveData<Event<OAuthProvider>> getOAuthRequest() {
        return this.oauthRequest;
    }

    public boolean isGoogleAvailable() {
        return OAuthProvider.GOOGLE.isConfigured();
    }

    public boolean isMicrosoftAvailable() {
        return OAuthProvider.MICROSOFT.isConfigured();
    }

    private String getEmailAddressValue() {
        return Strings.nullToEmpty(this.emailAddress.getValue()).trim();
    }

    // ------------------------------------------------------------------ step 1: email address

    public boolean checkEmailAddress() {
        final String emailAddress = getEmailAddressValue();
        if (EmailAddressUtil.isValid(emailAddress)) {
            return true;
        }
        emailAddressError.postValue(
                getApplication()
                        .getString(
                                emailAddress.isEmpty()
                                        ? R.string.enter_an_email_address
                                        : R.string.enter_a_valid_email_address));
        return false;
    }

    public void enterEmailAddress() {
        this.password.setValue(null);
        if (!checkEmailAddress()) {
            return;
        }
        final String emailAddress = getEmailAddressValue();
        if (AppDatabase.getInstance(getApplication()).accountDao().hasAccount(emailAddress)) {
            emailAddressError.postValue(getApplication().getString(R.string.account_already_exists));
            return;
        }
        this.loading.postValue(true);
        final ListenableFuture<MailSettings> discovery =
                NETWORK.submit(() -> Autoconfig.discover(emailAddress));
        this.networkFuture = discovery;
        Futures.addCallback(
                discovery,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final MailSettings settings) {
                        loading.postValue(false);
                        applySettings(settings);
                        final OAuthProvider provider = settings.getOauthProvider();
                        if (provider != null && provider.isConfigured()) {
                            oauthRequest.postValue(new Event<>(provider));
                        } else if (settings.getDiscovered()) {
                            preparePasswordStep(provider);
                            redirection.postValue(new Event<>(Target.ENTER_PASSWORD));
                        } else {
                            serverSettingsError.postValue(null);
                            redirection.postValue(new Event<>(Target.ENTER_SERVER_SETTINGS));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        loading.postValue(false);
                        if (!interruptedOrCancelled(throwable)) {
                            LOGGER.warn("Server discovery failed", throwable);
                            redirection.postValue(new Event<>(Target.ENTER_SERVER_SETTINGS));
                        }
                    }
                },
                MoreExecutors.directExecutor());
    }

    /** "Sign in with Google/Microsoft" buttons on the first screen. */
    public void signInWith(final OAuthProvider provider) {
        final String emailAddress = getEmailAddressValue();
        final String address = EmailAddressUtil.isValid(emailAddress) ? emailAddress : "";
        applySettings(
                provider == OAuthProvider.GOOGLE
                        ? Autoconfig.google(address)
                        : Autoconfig.microsoft(address));
        oauthRequest.postValue(new Event<>(provider));
    }

    private void applySettings(final MailSettings settings) {
        pop3.postValue("pop3".equals(settings.getProtocol()));
        incomingHost.postValue(settings.getIncomingHost());
        incomingPort.postValue(String.valueOf(settings.getIncomingPort()));
        incomingSecurity.postValue(settings.getIncomingSecurity());
        smtpHost.postValue(settings.getSmtpHost());
        smtpPort.postValue(String.valueOf(settings.getSmtpPort()));
        smtpSecurity.postValue(settings.getSmtpSecurity());
        username.postValue(settings.getUsername());
    }

    private void preparePasswordStep(final OAuthProvider provider) {
        passwordError.postValue(null);
        if (provider != null) {
            passwordHint.postValue(getApplication().getString(R.string.app_password));
            passwordExplanation.postValue(
                    getApplication()
                            .getString(
                                    provider == OAuthProvider.GOOGLE
                                            ? R.string.app_password_explanation_google
                                            : R.string.app_password_explanation_microsoft));
        } else {
            passwordHint.postValue(getApplication().getString(R.string.password));
            passwordExplanation.postValue(null);
        }
    }

    // ------------------------------------------------------------------ step 2a: password

    public boolean enterPassword() {
        final String password = Strings.nullToEmpty(this.password.getValue());
        if (password.isEmpty()) {
            this.passwordError.postValue(getApplication().getString(R.string.enter_a_password));
            return true;
        }
        final CredentialsEntity credentials;
        try {
            credentials = buildCredentials(password, null);
        } catch (final IllegalArgumentException e) {
            redirection.postValue(new Event<>(Target.ENTER_SERVER_SETTINGS));
            return true;
        }
        checkAndStore(credentials, null, Target.ENTER_PASSWORD);
        return true;
    }

    // ------------------------------------------------------------------ step 2b: manual settings

    public boolean enterServerSettings() {
        final String password = Strings.nullToEmpty(this.password.getValue());
        if (password.isEmpty()) {
            this.serverSettingsError.postValue(getApplication().getString(R.string.enter_a_password));
            return true;
        }
        final CredentialsEntity credentials;
        try {
            credentials = buildCredentials(password, null);
        } catch (final IllegalArgumentException e) {
            this.serverSettingsError.postValue(e.getMessage());
            return true;
        }
        checkAndStore(credentials, null, Target.ENTER_SERVER_SETTINGS);
        return true;
    }

    public void openServerSettings() {
        serverSettingsError.postValue(null);
        redirection.postValue(new Event<>(Target.ENTER_SERVER_SETTINGS));
    }

    // ------------------------------------------------------------------ step 2c: OAuth

    /** Called by the activity after the browser sign-in and the code exchange succeeded. */
    public void completeOAuth(
            final OAuthProvider provider,
            final String authState,
            final String accessToken,
            final String idToken) {
        final Set<String> emails = OAuthProvider.emailsFromIdToken(idToken);
        final String entered = getEmailAddressValue();
        final String fromToken = emails.isEmpty() ? null : emails.iterator().next();
        // The server wants the account's primary address as login name.
        final String login =
                emails.contains(entered) || fromToken == null ? entered : fromToken;
        final String email = EmailAddressUtil.isValid(entered) ? entered : login;
        if (Strings.isNullOrEmpty(email) || Strings.isNullOrEmpty(login)) {
            loading.postValue(false);
            showWarningMessage(R.string.could_not_determine_email_address);
            return;
        }
        emailAddress.setValue(email);
        final MailSettings defaults =
                provider == OAuthProvider.GOOGLE ? Autoconfig.google(email) : Autoconfig.microsoft(email);
        final CredentialsEntity credentials = new CredentialsEntity();
        credentials.incomingProtocol = "imap";
        credentials.incomingHost = valueOr(incomingHost, defaults.getIncomingHost());
        credentials.incomingPort = parsePort(valueOr(incomingPort, String.valueOf(defaults.getIncomingPort())));
        credentials.incomingSecurity = valueOr(incomingSecurity, defaults.getIncomingSecurity());
        credentials.smtpHost = valueOr(smtpHost, defaults.getSmtpHost());
        credentials.smtpPort = parsePort(valueOr(smtpPort, String.valueOf(defaults.getSmtpPort())));
        credentials.smtpSecurity = valueOr(smtpSecurity, defaults.getSmtpSecurity());
        if (OAuthProvider.forHost(credentials.incomingHost) != provider) {
            credentials.incomingHost = defaults.getIncomingHost();
            credentials.incomingPort = defaults.getIncomingPort();
            credentials.incomingSecurity = defaults.getIncomingSecurity();
            credentials.smtpHost = defaults.getSmtpHost();
            credentials.smtpPort = defaults.getSmtpPort();
            credentials.smtpSecurity = defaults.getSmtpSecurity();
        }
        credentials.authType = AuthType.XOAUTH2.name();
        credentials.username = login;
        credentials.oauthProvider = provider.getId();
        credentials.oauthState = authState;
        checkAndStore(credentials, new StaticTokenProvider(accessToken, emails), Target.SIGN_IN);
    }

    public void oauthFailed(final String message) {
        loading.postValue(false);
        if (message != null) {
            showWarningMessage(getApplication().getString(R.string.sign_in_failed_x, message));
        }
    }

    public void setLoading(final boolean loading) {
        this.loading.postValue(loading);
    }

    // ------------------------------------------------------------------ check and store

    private CredentialsEntity buildCredentials(final String password, final String oauthProvider) {
        final CredentialsEntity credentials = new CredentialsEntity();
        credentials.incomingProtocol = Boolean.TRUE.equals(pop3.getValue()) ? "pop3" : "imap";
        credentials.incomingHost = requireHost(incomingHost.getValue());
        credentials.incomingPort = parsePort(incomingPort.getValue());
        credentials.incomingSecurity = security(incomingSecurity.getValue());
        credentials.smtpHost = requireHost(smtpHost.getValue());
        credentials.smtpPort = parsePort(smtpPort.getValue());
        credentials.smtpSecurity = security(smtpSecurity.getValue());
        credentials.authType = AuthType.PLAIN.name();
        final String user = Strings.nullToEmpty(username.getValue()).trim();
        credentials.username = user.isEmpty() ? getEmailAddressValue() : user;
        credentials.password = password;
        credentials.oauthProvider = oauthProvider;
        return credentials;
    }

    private void checkAndStore(
            final CredentialsEntity credentials,
            final OAuth2TokenProvider tokenProvider,
            final Target errorTarget) {
        this.loading.postValue(true);
        this.passwordError.postValue(null);
        this.serverSettingsError.postValue(null);
        final String email = getEmailAddressValue().isEmpty() ? credentials.username : getEmailAddressValue();
        final ListenableFuture<Void> check =
                NETWORK.submit(
                        () -> {
                            Mua.checkSettings(getApplication(), toCredentials(credentials), tokenProvider);
                            return null;
                        });
        this.networkFuture = check;
        final ListenableFuture<Long> insert =
                Futures.transformAsync(
                        check,
                        v -> mainRepository.insertAccount(credentials, email, null),
                        MoreExecutors.directExecutor());
        Futures.addCallback(
                insert,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Long accountId) {
                        primaryAccountId = accountId;
                        LttrsApplication.get(getApplication())
                                .invalidateMostRecentlySelectedAccountId();
                        mainRepository.setSelectedAccount(accountId);
                        redirection.postValue(new Event<>(Target.DONE));
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        loading.postValue(false);
                        if (interruptedOrCancelled(throwable)) {
                            return;
                        }
                        LOGGER.warn("Account check failed", throwable);
                        onCheckFailed(throwable, errorTarget, credentials);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void onCheckFailed(
            final Throwable throwable, final Target errorTarget, final CredentialsEntity credentials) {
        final String message = describe(throwable);
        if (throwable instanceof AuthenticationFailedException && errorTarget == Target.ENTER_PASSWORD) {
            final OAuthProvider provider = OAuthProvider.forHost(credentials.incomingHost);
            preparePasswordStep(provider);
            passwordError.postValue(getApplication().getString(R.string.wrong_password));
            return;
        }
        if (errorTarget == Target.SIGN_IN) {
            showWarningMessage(message);
            return;
        }
        serverSettingsError.postValue(message);
        if (errorTarget != Target.ENTER_SERVER_SETTINGS) {
            redirection.postValue(new Event<>(Target.ENTER_SERVER_SETTINGS));
        }
    }

    private String describe(final Throwable throwable) {
        if (throwable instanceof AuthenticationFailedException) {
            final String server = ((AuthenticationFailedException) throwable).getMessageFromServer();
            return getApplication().getString(R.string.wrong_password)
                    + (server == null ? "" : " (" + server + ")");
        }
        if (throwable instanceof CertificateValidationException || rootCause(throwable) instanceof SSLException) {
            return getApplication().getString(R.string.unable_to_establish_secure_connection);
        }
        final Throwable root = rootCause(throwable);
        if (root instanceof UnknownHostException) {
            return isNetworkAvailable()
                    ? getApplication().getString(R.string.unknown_host, root.getMessage())
                    : getApplication().getString(R.string.no_network_connection);
        }
        if (root instanceof ConnectException) {
            return getApplication().getString(R.string.unable_to_connect);
        }
        if (root instanceof SocketTimeoutException) {
            return getApplication().getString(R.string.timeout_reached);
        }
        final String message = throwable.getMessage();
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    private static Throwable rootCause(final Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static AccountWithCredentials.Credentials toCredentials(final CredentialsEntity c) {
        return new AccountWithCredentials.Credentials(
                -1L,
                c.incomingProtocol,
                c.incomingHost,
                c.incomingPort,
                c.incomingSecurity,
                c.smtpHost,
                c.smtpPort,
                c.smtpSecurity,
                c.authType,
                c.username,
                c.password,
                c.oauthProvider);
    }

    private String requireHost(final String host) {
        final String value = Strings.nullToEmpty(host).trim();
        if (value.isEmpty() || value.contains(" ") || value.contains("/")) {
            throw new IllegalArgumentException(getApplication().getString(R.string.enter_a_server_name));
        }
        return value;
    }

    private int parsePort(final String port) {
        try {
            final int value = Integer.parseInt(Strings.nullToEmpty(port).trim());
            if (value > 0 && value < 65536) {
                return value;
            }
        } catch (final NumberFormatException e) {
            // fall through
        }
        throw new IllegalArgumentException(getApplication().getString(R.string.enter_a_valid_port));
    }

    private static String security(final String value) {
        for (final String option : SECURITY_OPTIONS) {
            if (option.equals(value)) {
                return option;
            }
        }
        return SECURITY_OPTIONS[0];
    }

    private static String valueOr(final LiveData<String> liveData, final String fallback) {
        final String value = liveData.getValue();
        return Strings.isNullOrEmpty(value) ? fallback : value;
    }

    // ------------------------------------------------------------------ misc

    private void showWarningMessage(final @StringRes int res) {
        showWarningMessage(getApplication().getString(res));
    }

    private void showWarningMessage(final String message) {
        this.warningMessage.postValue(new Event<>(message));
    }

    private static boolean interruptedOrCancelled(final Throwable t) {
        return t instanceof InterruptedException || t instanceof CancellationException;
    }

    public boolean cancel() {
        final ListenableFuture<?> currentNetworkFuture = this.networkFuture;
        if (currentNetworkFuture == null || currentNetworkFuture.isDone()) {
            return false;
        }
        loading.postValue(false);
        return currentNetworkFuture.cancel(true);
    }

    private boolean isNetworkAvailable() {
        final ConnectivityManager cm = getApplication().getSystemService(ConnectivityManager.class);
        final Network activeNetwork = cm == null ? null : cm.getActiveNetwork();
        final NetworkCapabilities capabilities =
                activeNetwork == null ? null : cm.getNetworkCapabilities(activeNetwork);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public long getPrimaryAccountId() {
        final Long accountId = this.primaryAccountId;
        if (accountId == null) {
            throw new IllegalStateException(
                    "Trying to access accountId before Target.DONE event occurred");
        }
        return accountId;
    }

    public enum Target {
        SIGN_IN,
        ENTER_PASSWORD,
        ENTER_SERVER_SETTINGS,
        DONE
    }
}
