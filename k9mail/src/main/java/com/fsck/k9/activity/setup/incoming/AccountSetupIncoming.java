
package com.fsck.k9.activity.setup.incoming;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;

import com.fsck.k9.activity.K9Activity;
import com.fsck.k9.activity.setup.outgoing.AccountSetupOutgoing;
import com.fsck.k9.activity.setup.AuthTypeAdapter;
import com.fsck.k9.activity.setup.AuthTypeHolder;
import com.fsck.k9.activity.setup.ConnectionSecurityAdapter;
import com.fsck.k9.activity.setup.ConnectionSecurityHolder;
import com.fsck.k9.activity.setup.checksettings.AccountSetupCheckSettings;
import com.fsck.k9.activity.setup.checksettings.CheckSettingsPresenter.CheckDirection;
import com.fsck.k9.activity.setup.incoming.IncomingContract.Presenter;
import timber.log.Timber;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.fsck.k9.*;
import com.fsck.k9.mail.NetworkType;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.ServerSettings;
import com.fsck.k9.mail.ServerSettings.Type;
import com.fsck.k9.mail.store.RemoteStore;
import com.fsck.k9.mail.store.imap.ImapStoreSettings;
import com.fsck.k9.mail.store.webdav.WebDavStoreSettings;
import com.fsck.k9.account.AccountCreator;
import com.fsck.k9.view.ClientCertificateSpinner;
import com.fsck.k9.view.ClientCertificateSpinner.OnClientCertificateChangedListener;


public class AccountSetupIncoming extends K9Activity implements OnClickListener, IncomingContract.View {
    private static final String EXTRA_ACCOUNT = "account";
    private static final String EXTRA_MAKE_DEFAULT = "makeDefault";
    private static final String STATE_SECURITY_TYPE_POSITION = "stateSecurityTypePosition";
    private static final String STATE_AUTH_TYPE_POSITION = "authTypePosition";

    private Type mStoreType;
    private EditText mUsernameView;
    private EditText mPasswordView;
    private ClientCertificateSpinner mClientCertificateSpinner;
    private TextView mClientCertificateLabelView;
    private TextView mPasswordLabelView;
    private EditText mServerView;
    private EditText mPortView;
    private String mCurrentPortViewSetting;
    private Spinner mSecurityTypeView;
    private int mCurrentSecurityTypeViewPosition;
    private Spinner mAuthTypeView;
    private int mCurrentAuthTypeViewPosition;
    private CheckBox mImapAutoDetectNamespaceView;
    private EditText mImapPathPrefixView;
    private EditText mWebdavPathPrefixView;
    private EditText mWebdavAuthPathView;
    private EditText mWebdavMailboxPathView;
    private Button mNextButton;
    private boolean mMakeDefault;
    private CheckBox mCompressionMobile;
    private CheckBox mCompressionWifi;
    private CheckBox mCompressionOther;
    private CheckBox mSubscribedFoldersOnly;
    private AuthTypeAdapter mAuthTypeAdapter;
    private ConnectionSecurity[] mConnectionSecurityChoices = ConnectionSecurity.values();
    private Presenter presenter;

    public static void actionIncomingSettings(Activity context, Account account, boolean makeDefault) {
        Intent i = new Intent(context, AccountSetupIncoming.class);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_MAKE_DEFAULT, makeDefault);
        context.startActivity(i);
    }

    public static void actionEditIncomingSettings(Activity context, Account account) {
        context.startActivity(intentActionEditIncomingSettings(context, account));
    }

    public static Intent intentActionEditIncomingSettings(Context context, Account account) {
        Intent i = new Intent(context, AccountSetupIncoming.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        return i;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_setup_incoming);

        mUsernameView = (EditText)findViewById(R.id.account_username);
        mPasswordView = (EditText)findViewById(R.id.account_password);
        mClientCertificateSpinner = (ClientCertificateSpinner)findViewById(R.id.account_client_certificate_spinner);
        mClientCertificateLabelView = (TextView)findViewById(R.id.account_client_certificate_label);
        mPasswordLabelView = (TextView)findViewById(R.id.account_password_label);
        TextView serverLabelView = (TextView) findViewById(R.id.account_server_label);
        mServerView = (EditText)findViewById(R.id.account_server);
        mPortView = (EditText)findViewById(R.id.account_port);
        mSecurityTypeView = (Spinner)findViewById(R.id.account_security_type);
        mAuthTypeView = (Spinner)findViewById(R.id.account_auth_type);
        mImapAutoDetectNamespaceView = (CheckBox)findViewById(R.id.imap_autodetect_namespace);
        mImapPathPrefixView = (EditText)findViewById(R.id.imap_path_prefix);
        mWebdavPathPrefixView = (EditText)findViewById(R.id.webdav_path_prefix);
        mWebdavAuthPathView = (EditText)findViewById(R.id.webdav_auth_path);
        mWebdavMailboxPathView = (EditText)findViewById(R.id.webdav_mailbox_path);
        mNextButton = (Button)findViewById(R.id.next);
        mCompressionMobile = (CheckBox)findViewById(R.id.compression_mobile);
        mCompressionWifi = (CheckBox)findViewById(R.id.compression_wifi);
        mCompressionOther = (CheckBox)findViewById(R.id.compression_other);
        mSubscribedFoldersOnly = (CheckBox)findViewById(R.id.subscribed_folders_only);

        mNextButton.setOnClickListener(this);

        mImapAutoDetectNamespaceView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mImapPathPrefixView.setEnabled(!isChecked);
                if (isChecked && mImapPathPrefixView.hasFocus()) {
                    mImapPathPrefixView.focusSearch(View.FOCUS_UP).requestFocus();
                } else if (!isChecked) {
                    mImapPathPrefixView.requestFocus();
                }
            }
        });

        mAuthTypeAdapter = AuthTypeAdapter.get(this);
        mAuthTypeView.setAdapter(mAuthTypeAdapter);

        /*
         * Only allow digits in the port field.
         */
        mPortView.setKeyListener(DigitsKeyListener.getInstance("0123456789"));

        String accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        Account account = Preferences.getPreferences(this).getAccount(accountUuid);
        mMakeDefault = getIntent().getBooleanExtra(EXTRA_MAKE_DEFAULT, false);

        /*
         * If we're being reloaded we override the original account with the one
         * we saved
         */
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_ACCOUNT)) {
            accountUuid = savedInstanceState.getString(EXTRA_ACCOUNT);
            account = Preferences.getPreferences(this).getAccount(accountUuid);
        }

        presenter = new IncomingPresenter(this, account);

        boolean editSettings = Intent.ACTION_EDIT.equals(getIntent().getAction());

        try {
            ServerSettings settings = RemoteStore.decodeStoreUri(account.getStoreUri());

            if (savedInstanceState == null) {
                // The first item is selected if settings.authenticationType is null or is not in mAuthTypeAdapter
                mCurrentAuthTypeViewPosition = mAuthTypeAdapter.getAuthPosition(settings.authenticationType);
            } else {
                mCurrentAuthTypeViewPosition = savedInstanceState.getInt(STATE_AUTH_TYPE_POSITION);
            }
            mAuthTypeView.setSelection(mCurrentAuthTypeViewPosition, false);
            updateViewFromAuthType();

            if (settings.username != null) {
                mUsernameView.setText(settings.username);
            }

            if (settings.password != null) {
                mPasswordView.setText(settings.password);
            }

            if (settings.clientCertificateAlias != null) {
                mClientCertificateSpinner.setAlias(settings.clientCertificateAlias);
            }

            mStoreType = settings.type;
            if (Type.POP3 == settings.type) {
                serverLabelView.setText(R.string.account_setup_incoming_pop_server_label);
                findViewById(R.id.imap_path_prefix_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_advanced_header).setVisibility(View.GONE);
                findViewById(R.id.webdav_mailbox_alias_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_owa_path_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_auth_path_section).setVisibility(View.GONE);
                findViewById(R.id.compression_section).setVisibility(View.GONE);
                findViewById(R.id.compression_label).setVisibility(View.GONE);
                mSubscribedFoldersOnly.setVisibility(View.GONE);
            } else if (Type.IMAP == settings.type) {
                serverLabelView.setText(R.string.account_setup_incoming_imap_server_label);

                ImapStoreSettings imapSettings = (ImapStoreSettings) settings;

                mImapAutoDetectNamespaceView.setChecked(imapSettings.autoDetectNamespace);
                if (imapSettings.pathPrefix != null) {
                    mImapPathPrefixView.setText(imapSettings.pathPrefix);
                }

                findViewById(R.id.webdav_advanced_header).setVisibility(View.GONE);
                findViewById(R.id.webdav_mailbox_alias_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_owa_path_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_auth_path_section).setVisibility(View.GONE);

                if (!editSettings) {
                    findViewById(R.id.imap_folder_setup_section).setVisibility(View.GONE);
                }
            } else if (Type.WebDAV == settings.type) {
                serverLabelView.setText(R.string.account_setup_incoming_webdav_server_label);
                mConnectionSecurityChoices = new ConnectionSecurity[] {
                        ConnectionSecurity.NONE,
                        ConnectionSecurity.SSL_TLS_REQUIRED };

                // Hide the unnecessary fields
                findViewById(R.id.imap_path_prefix_section).setVisibility(View.GONE);
                findViewById(R.id.account_auth_type_label).setVisibility(View.GONE);
                findViewById(R.id.account_auth_type).setVisibility(View.GONE);
                findViewById(R.id.compression_section).setVisibility(View.GONE);
                findViewById(R.id.compression_label).setVisibility(View.GONE);
                mSubscribedFoldersOnly.setVisibility(View.GONE);

                WebDavStoreSettings webDavSettings = (WebDavStoreSettings) settings;

                if (webDavSettings.path != null) {
                    mWebdavPathPrefixView.setText(webDavSettings.path);
                }

                if (webDavSettings.authPath != null) {
                    mWebdavAuthPathView.setText(webDavSettings.authPath);
                }

                if (webDavSettings.mailboxPath != null) {
                    mWebdavMailboxPathView.setText(webDavSettings.mailboxPath);
                }
            } else {
                throw new Exception("Unknown account type: " + account.getStoreUri());
            }

            if (!editSettings) {
                account.setDeletePolicy(AccountCreator.getDefaultDeletePolicy(settings.type));
            }

            // Note that mConnectionSecurityChoices is configured above based on server type
            ConnectionSecurityAdapter securityTypesAdapter =
                    ConnectionSecurityAdapter.get(this, mConnectionSecurityChoices);
            mSecurityTypeView.setAdapter(securityTypesAdapter);

            // Select currently configured security type
            if (savedInstanceState == null) {
                mCurrentSecurityTypeViewPosition = securityTypesAdapter.getConnectionSecurityPosition(settings.connectionSecurity);
            } else {

                /*
                 * Restore the spinner state now, before calling
                 * setOnItemSelectedListener(), thus avoiding a call to
                 * onItemSelected(). Then, when the system restores the state
                 * (again) in onRestoreInstanceState(), The system will see that
                 * the new state is the same as the current state (set here), so
                 * once again onItemSelected() will not be called.
                 */
                mCurrentSecurityTypeViewPosition = savedInstanceState.getInt(STATE_SECURITY_TYPE_POSITION);
            }
            mSecurityTypeView.setSelection(mCurrentSecurityTypeViewPosition, false);

            updateAuthPlainTextFromSecurityType(settings.connectionSecurity);

            mCompressionMobile.setChecked(account.useCompression(NetworkType.MOBILE));
            mCompressionWifi.setChecked(account.useCompression(NetworkType.WIFI));
            mCompressionOther.setChecked(account.useCompression(NetworkType.OTHER));

            if (settings.host != null) {
                mServerView.setText(settings.host);
            }

            if (settings.port != -1) {
                mPortView.setText(String.format("%d", settings.port));
            } else {
                updatePortFromSecurityType();
            }
            mCurrentPortViewSetting = mPortView.getText().toString();

            mSubscribedFoldersOnly.setChecked(account.subscribedFoldersOnly());
        } catch (Exception e) {
            failure(e);
        }
    }

    /**
     * Called at the end of either {@code onCreate()} or
     * {@code onRestoreInstanceState()}, after the views have been initialized,
     * so that the listeners are not triggered during the view initialization.
     * This avoids needless calls to {@code validateFields()} which is called
     * immediately after this is called.
     */
    private void initializeViewListeners() {

        /*
         * Updates the port when the user changes the security type. This allows
         * us to show a reasonable default which the user can change.
         */
        mSecurityTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {

                /*
                 * We keep our own record of the spinner state so we
                 * know for sure that onItemSelected() was called
                 * because of user input, not because of spinner
                 * state initialization. This assures that the port
                 * will not be replaced with a default value except
                 * on user input.
                 */
                if (mCurrentSecurityTypeViewPosition != position) {
                    updatePortFromSecurityType();
                    validateFields();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* unused */ }
        });

        mAuthTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {
                if (mCurrentAuthTypeViewPosition == position) {
                    return;
                }

                updateViewFromAuthType();
                validateFields();
                AuthType selection = getSelectedAuthType();

                // Have the user select (or confirm) the client certificate
                if (AuthType.EXTERNAL == selection) {

                    // This may again invoke validateFields()
                    mClientCertificateSpinner.chooseCertificate();
                } else {
                    mPasswordView.requestFocus();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* unused */ }
        });

        mClientCertificateSpinner.setOnClientCertificateChangedListener(clientCertificateChangedListener);
        mUsernameView.addTextChangedListener(validationTextWatcher);
        mPasswordView.addTextChangedListener(validationTextWatcher);
        mServerView.addTextChangedListener(validationTextWatcher);
        mPortView.addTextChangedListener(validationTextWatcher);
    }

    private void validateFields() {
        presenter.revokeInvalidSettings(getSelectedAuthType(), getSelectedSecurity());
        presenter.validateFields(mClientCertificateSpinner.getAlias(), mServerView.getText().toString(),
                mPortView.getText().toString(), mUsernameView.getText().toString(),
                mPasswordView.getText().toString(), getSelectedAuthType(), getSelectedSecurity());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_ACCOUNT, presenter.getAccount().getUuid());
        outState.putInt(STATE_SECURITY_TYPE_POSITION, mCurrentSecurityTypeViewPosition);
        outState.putInt(STATE_AUTH_TYPE_POSITION, mCurrentAuthTypeViewPosition);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        /*
         * We didn't want the listeners active while the state was being restored
         * because they could overwrite the restored port with a default port when
         * the security type was restored.
         */
        initializeViewListeners();
        validateFields();
    }

    /**
     * Shows/hides password field and client certificate spinner
     */
    private void updateViewFromAuthType() {
        AuthType authType = getSelectedAuthType();
        boolean isAuthTypeExternal = (AuthType.EXTERNAL == authType);

        if (isAuthTypeExternal) {

            // hide password fields, show client certificate fields
            mPasswordView.setVisibility(View.GONE);
            mPasswordLabelView.setVisibility(View.GONE);
            mClientCertificateLabelView.setVisibility(View.VISIBLE);
            mClientCertificateSpinner.setVisibility(View.VISIBLE);
        } else {

            // show password fields, hide client certificate fields
            mPasswordView.setVisibility(View.VISIBLE);
            mPasswordLabelView.setVisibility(View.VISIBLE);
            mClientCertificateLabelView.setVisibility(View.GONE);
            mClientCertificateSpinner.setVisibility(View.GONE);
        }
    }


    private void updatePortFromSecurityType() {
        ConnectionSecurity securityType = getSelectedSecurity();
        updateAuthPlainTextFromSecurityType(securityType);

        // Remove listener so as not to trigger validateFields() which is called
        // elsewhere as a result of user interaction.
        mPortView.removeTextChangedListener(validationTextWatcher);
        mPortView.setText(String.valueOf(AccountCreator.getDefaultPort(securityType, mStoreType)));
        mPortView.addTextChangedListener(validationTextWatcher);
    }

    private void updateAuthPlainTextFromSecurityType(ConnectionSecurity securityType) {
        mAuthTypeAdapter.useInsecureText(securityType == ConnectionSecurity.NONE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (Intent.ACTION_EDIT.equals(getIntent().getAction())) {
                presenter.updateAccount();
                finish();
            } else {
                /*
                 * Set the username and password for the outgoing settings to the username and
                 * password the user just set for incoming.
                 */
                String username = mUsernameView.getText().toString();
                String password = mPasswordView.getText().toString();
                String clientCertificateAlias = mClientCertificateSpinner.getAlias();
                AuthType authType = getSelectedAuthType();

                presenter.prepareForOutgoing(username, password, clientCertificateAlias, authType);

                finish();
            }
        }
    }

    protected void onNext() {
        try {
            ConnectionSecurity connectionSecurity = getSelectedSecurity();

            String username = mUsernameView.getText().toString();
            String password = mPasswordView.getText().toString();
            String clientCertificateAlias = mClientCertificateSpinner.getAlias();
            boolean autoDetectNamespace = mImapAutoDetectNamespaceView.isChecked();
            String imapPathPrefix = mImapPathPrefixView.getText().toString();
            String webdavPathPrefix = mWebdavPathPrefixView.getText().toString();
            String webdavAuthPath = mWebdavAuthPathView.getText().toString();
            String webdavMailboxPath = mWebdavMailboxPathView.getText().toString();

            AuthType authType = getSelectedAuthType();

            String host = mServerView.getText().toString();
            int port = Integer.parseInt(mPortView.getText().toString());

            boolean compressMobile = mCompressionMobile.isChecked();
            boolean compressWifi = mCompressionWifi.isChecked();
            boolean compressOther = mCompressionOther.isChecked();
            boolean subscribedFoldersOnly = mSubscribedFoldersOnly.isChecked();

            presenter.modifyAccount(username, password, clientCertificateAlias, autoDetectNamespace, imapPathPrefix,
                    webdavPathPrefix, webdavAuthPath, webdavMailboxPath, host, port, connectionSecurity, authType,
                    compressMobile, compressWifi, compressOther, subscribedFoldersOnly);

            AccountSetupCheckSettings.startChecking(this, presenter.getAccount(), CheckDirection.INCOMING);
        } catch (Exception e) {
            failure(e);
        }

    }

    public void onClick(View v) {
        try {
            switch (v.getId()) {
            case R.id.next:
                onNext();
                break;
            }
        } catch (Exception e) {
            failure(e);
        }
    }

    private void failure(Exception use) {
        Timber.e(use, "Failure");
        String toastText = getString(R.string.account_setup_bad_uri, use.getMessage());

        Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_LONG);
        toast.show();
    }


    /*
     * Calls validateFields() which enables or disables the Next button
     * based on the fields' validity.
     */
    TextWatcher validationTextWatcher = new TextWatcher() {
        public void afterTextChanged(Editable s) {
            validateFields();
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            /* unused */
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            /* unused */
        }
    };

    OnClientCertificateChangedListener clientCertificateChangedListener = new OnClientCertificateChangedListener() {
        @Override
        public void onClientCertificateChanged(String alias) {
            validateFields();
        }
    };

    private AuthType getSelectedAuthType() {
        AuthTypeHolder holder = (AuthTypeHolder) mAuthTypeView.getSelectedItem();
        return holder.getAuthType();
    }

    private ConnectionSecurity getSelectedSecurity() {
        ConnectionSecurityHolder holder = (ConnectionSecurityHolder) mSecurityTypeView.getSelectedItem();
        return holder.getConnectionSecurity();
    }

    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void goToOutgoingSettings(Account account) {
        AccountSetupOutgoing.actionOutgoingSettings(this, account, mMakeDefault);
    }

    @Override
    public void resetPreviousSettings() {
        // Notify user of an invalid combination of AuthType.EXTERNAL & ConnectionSecurity.NONE
        String toastText = getString(R.string.account_setup_incoming_invalid_setting_combo_notice,
                getString(R.string.account_setup_incoming_auth_type_label),
                AuthType.EXTERNAL.toString(),
                getString(R.string.account_setup_incoming_security_label),
                ConnectionSecurity.NONE.toString());
        Toast.makeText(this, toastText, Toast.LENGTH_LONG).show();

        // Reset the views back to their previous settings without recursing through here again
        OnItemSelectedListener onItemSelectedListener = mAuthTypeView.getOnItemSelectedListener();
        mAuthTypeView.setOnItemSelectedListener(null);
        mAuthTypeView.setSelection(mCurrentAuthTypeViewPosition, false);
        mAuthTypeView.setOnItemSelectedListener(onItemSelectedListener);
        updateViewFromAuthType();

        onItemSelectedListener = mSecurityTypeView.getOnItemSelectedListener();
        mSecurityTypeView.setOnItemSelectedListener(null);
        mSecurityTypeView.setSelection(mCurrentSecurityTypeViewPosition, false);
        mSecurityTypeView.setOnItemSelectedListener(onItemSelectedListener);
        updateAuthPlainTextFromSecurityType(getSelectedSecurity());

        mPortView.removeTextChangedListener(validationTextWatcher);
        mPortView.setText(mCurrentPortViewSetting);
        mPortView.addTextChangedListener(validationTextWatcher);
    }

    @Override
    public void saveSettings() {
        mCurrentAuthTypeViewPosition = mAuthTypeView.getSelectedItemPosition();
        mCurrentSecurityTypeViewPosition = mSecurityTypeView.getSelectedItemPosition();
        mCurrentPortViewSetting = mPortView.getText().toString();
    }

    @Override
    public void enableNext(boolean enabled) {
        mNextButton.setEnabled(enabled);
        Utility.setCompoundDrawablesAlpha(mNextButton, mNextButton.isEnabled() ? 255 : 128);
    }

    @Override
    public Context getContext() {
        return this;
    }
}
