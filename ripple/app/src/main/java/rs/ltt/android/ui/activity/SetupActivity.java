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

package rs.ltt.android.ui.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.ResponseTypeValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.R;
import rs.ltt.android.SetupNavigationDirections;
import rs.ltt.android.databinding.ActivitySetupBinding;
import rs.ltt.android.engine.OAuthProvider;
import rs.ltt.android.mail.util.EmailAddressUtil;
import rs.ltt.android.mail.util.MailToUri;
import rs.ltt.android.ui.MaterialAlertDialogs;
import rs.ltt.android.ui.model.SetupViewModel;
import rs.ltt.android.util.Event;
import rs.ltt.android.util.NavControllers;

public class SetupActivity extends AppCompatActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetupActivity.class);

    public static String EXTRA_NEXT_ACTION = "rs.ltt.android.extras.next-action";

    /** Account id whose login the server rejected; sign in to it again. */
    public static final String EXTRA_REAUTH_ACCOUNT = "rs.ltt.android.extras.reauth-account";

    private static final String STATE_PENDING_PROVIDER = "pending-oauth-provider";
    private static final String STATE_REAUTH_ACCOUNT = "reauth-account";
    private SetupViewModel setupViewModel;
    private AuthorizationService authorizationService;
    private OAuthProvider pendingProvider;

    private final ActivityResultLauncher<Intent> oauthLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(), this::onOAuthResult);

    private final OnBackPressedCallback loadingBackPressedCallback =
            new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    if (setupViewModel.cancel()) {
                        LOGGER.info("Cancelled current tasks");
                    }
                }
            };

    public static void launch(final ComponentActivity activity) {
        final Intent intent = new Intent(activity, SetupActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivitySetupBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_setup);
        final ViewModelProvider viewModelProvider =
                new ViewModelProvider(this, getDefaultViewModelProviderFactory());
        this.setupViewModel = viewModelProvider.get(SetupViewModel.class);
        this.setupViewModel.getRedirection().observe(this, this::onRedirectionEvent);
        this.setupViewModel.getWarningMessage().observe(this, this::onWarningMessage);
        this.setupViewModel.getOAuthRequest().observe(this, this::onOAuthRequest);
        this.authorizationService = new AuthorizationService(this);
        if (savedInstanceState != null) {
            // Android may destroy this activity while the browser shows the sign-in page.
            this.pendingProvider =
                    OAuthProvider.of(savedInstanceState.getString(STATE_PENDING_PROVIDER));
            final long reauth = savedInstanceState.getLong(STATE_REAUTH_ACCOUNT, -1L);
            if (reauth >= 0 && setupViewModel.getReauthAccountId() == null) {
                setupViewModel.restoreReauth(reauth);
            }
        } else {
            final long reauth = getIntent().getLongExtra(EXTRA_REAUTH_ACCOUNT, -1L);
            if (reauth >= 0) {
                setupViewModel.startReauth(reauth);
            }
        }
        this.getOnBackPressedDispatcher().addCallback(this, this.loadingBackPressedCallback);
        this.setupViewModel
                .isLoading()
                .observe(
                        this,
                        loading ->
                                this.loadingBackPressedCallback.setEnabled(
                                        Boolean.TRUE.equals(loading)));
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.pendingProvider != null) {
            outState.putString(STATE_PENDING_PROVIDER, this.pendingProvider.getId());
        }
        final Long reauth = setupViewModel.getReauthAccountId();
        if (reauth != null) {
            outState.putLong(STATE_REAUTH_ACCOUNT, reauth);
        }
    }

    private void onWarningMessage(Event<String> event) {
        MaterialAlertDialogs.error(this, event);
    }

    private void onRedirectionEvent(final Event<SetupViewModel.Target> targetEvent) {
        if (targetEvent.isConsumable()) {
            final NavController navController = getNavController();
            final SetupViewModel.Target target = targetEvent.consume();
            switch (target) {
                case SIGN_IN -> {}
                case ENTER_PASSWORD ->
                        navController.navigate(SetupNavigationDirections.enterPassword());
                case ENTER_SERVER_SETTINGS ->
                        navController.navigate(SetupNavigationDirections.enterServerSettings());
                case DONE -> redirectToLttrs(this.setupViewModel.getPrimaryAccountId());
                default ->
                        throw new IllegalStateException(
                                String.format("Unable to navigate to target %s", target));
            }
        }
    }

    private void onOAuthRequest(final Event<OAuthProvider> event) {
        if (!event.isConsumable()) {
            return;
        }
        final OAuthProvider provider = event.consume();
        this.pendingProvider = provider;
        final AuthorizationRequest.Builder builder =
                new AuthorizationRequest.Builder(
                        provider.getServiceConfiguration(),
                        provider.getClientId(),
                        ResponseTypeValues.CODE,
                        provider.getRedirectUri());
        builder.setScopes(provider.getScopes());
        final String email = setupViewModel.getEmailAddress().getValue();
        if (email != null && EmailAddressUtil.isValid(email.trim())) {
            builder.setLoginHint(email.trim());
        }
        if (provider == OAuthProvider.MICROSOFT) {
            builder.setPrompt("select_account");
        }
        setupViewModel.setLoading(true);
        try {
            oauthLauncher.launch(
                    authorizationService.getAuthorizationRequestIntent(builder.build()));
        } catch (final ActivityNotFoundException e) {
            setupViewModel.oauthFailed(getString(R.string.no_browser_found));
        }
    }

    private void onOAuthResult(final ActivityResult result) {
        final Intent data = result.getData();
        final OAuthProvider provider = this.pendingProvider;
        if (data == null || provider == null) {
            setupViewModel.oauthFailed(null);
            return;
        }
        final AuthorizationResponse response = AuthorizationResponse.fromIntent(data);
        final AuthorizationException exception = AuthorizationException.fromIntent(data);
        if (response == null) {
            if (exception == null
                    || exception.code
                            == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code) {
                setupViewModel.oauthFailed(null);
            } else {
                setupViewModel.oauthFailed(describe(exception));
            }
            return;
        }
        final AuthState authState = new AuthState(response, exception);
        authorizationService.performTokenRequest(
                response.createTokenExchangeRequest(),
                (tokenResponse, tokenException) -> {
                    authState.update(tokenResponse, tokenException);
                    if (tokenResponse == null || tokenResponse.accessToken == null) {
                        setupViewModel.oauthFailed(
                                tokenException == null ? null : describe(tokenException));
                        return;
                    }
                    setupViewModel.completeOAuth(
                            provider,
                            authState.jsonSerializeString(),
                            tokenResponse.accessToken,
                            tokenResponse.idToken);
                });
    }

    private static String describe(final AuthorizationException exception) {
        if (exception.errorDescription != null) {
            return exception.errorDescription;
        }
        return exception.error != null ? exception.error : exception.getMessage();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.authorizationService != null) {
            this.authorizationService.dispose();
        }
    }

    private NavController getNavController() {
        return NavControllers.findNavController(this, R.id.nav_host_fragment);
    }

    private void redirectToLttrs(final Long accountId) {
        final Intent currentIntent = getIntent();
        final String uri =
                currentIntent == null ? null : currentIntent.getStringExtra(EXTRA_NEXT_ACTION);
        final MailToUri mailToUri = uri == null ? null : MailToUri.parse(uri);
        if (mailToUri != null) {
            ComposeActivity.launch(this, Uri.parse(uri));
        } else {
            LttrsActivity.launch(this, accountId, false);
        }
        finish();
    }
}
