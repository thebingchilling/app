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

package rs.ltt.android.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.MutableLiveData;
import rs.ltt.android.R;
import rs.ltt.android.databinding.FragmentServerSettingsBinding;
import rs.ltt.android.ui.model.SetupViewModel;

/** Manual IMAP/POP3 and SMTP settings, shown when discovery or the login test failed. */
public class ServerSettingsFragment extends AbstractSetupFragment {

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final FragmentServerSettingsBinding binding =
                DataBindingUtil.inflate(
                        inflater, R.layout.fragment_server_settings, container, false);
        binding.setSetupViewModel(setupViewModel);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.protocol.check(
                Boolean.TRUE.equals(setupViewModel.getPop3().getValue())
                        ? R.id.protocol_pop3
                        : R.id.protocol_imap);
        binding.protocol.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {
                    if (!isChecked) {
                        return;
                    }
                    final boolean pop3 = checkedId == R.id.protocol_pop3;
                    setupViewModel.getPop3().setValue(pop3);
                    final String port = setupViewModel.getIncomingPort().getValue();
                    if ("993".equals(port) || "995".equals(port) || port == null) {
                        setupViewModel.getIncomingPort().setValue(pop3 ? "995" : "993");
                    }
                });
        bindSecurity(binding.incomingSecurity, setupViewModel.getIncomingSecurity());
        bindSecurity(binding.smtpSecurity, setupViewModel.getSmtpSecurity());
        return binding.getRoot();
    }

    private void bindSecurity(
            final AutoCompleteTextView view, final MutableLiveData<String> liveData) {
        final String[] labels = {
            getString(R.string.security_ssl_tls),
            getString(R.string.security_starttls),
            getString(R.string.security_none)
        };
        view.setAdapter(
                new ArrayAdapter<>(
                        requireContext(), android.R.layout.simple_list_item_1, labels));
        liveData.observe(
                getViewLifecycleOwner(),
                value -> {
                    int index = 0;
                    for (int i = 0; i < SetupViewModel.SECURITY_OPTIONS.length; ++i) {
                        if (SetupViewModel.SECURITY_OPTIONS[i].equals(value)) {
                            index = i;
                        }
                    }
                    view.setText(labels[index], false);
                });
        view.setOnItemClickListener(
                (parent, v, position, id) ->
                        liveData.setValue(SetupViewModel.SECURITY_OPTIONS[position]));
    }
}
