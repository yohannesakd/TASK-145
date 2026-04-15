package com.roadrunner.dispatch.presentation.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.RoadRunnerApp;
import com.roadrunner.dispatch.core.domain.usecase.LoginUseCase;
import com.roadrunner.dispatch.infrastructure.db.AppDatabase;
import com.roadrunner.dispatch.infrastructure.repository.UserRepositoryImpl;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;

public class LoginFragment extends Fragment {

    private LoginViewModel viewModel;

    private TextInputEditText editUsername;
    private TextInputEditText editPassword;
    private MaterialButton btnLogin;
    private TextView textError;
    private ProgressBar progressLogin;

    public LoginFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Wire up views
        editUsername  = view.findViewById(R.id.edit_username);
        editPassword  = view.findViewById(R.id.edit_password);
        btnLogin      = view.findViewById(R.id.btn_login);
        textError     = view.findViewById(R.id.text_error);
        progressLogin = view.findViewById(R.id.progress_login);

        // Build dependencies
        AppDatabase db = RoadRunnerApp.getInstance().getDatabase();
        UserRepositoryImpl userRepository = new UserRepositoryImpl(db.userDao());
        LoginUseCase loginUseCase = new LoginUseCase(userRepository);
        SessionManager sessionManager = new SessionManager(requireContext());

        // Create ViewModel
        LoginViewModelFactory factory = new LoginViewModelFactory(loginUseCase, sessionManager);
        viewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);

        // If already logged in, navigate to the correct dashboard immediately
        if (viewModel.isLoggedIn()) {
            navigateForRole(view, viewModel.getCurrentRole());
            return;
        }

        // Login button click
        btnLogin.setOnClickListener(v -> attemptLogin(view));

        // Allow submitting from the password field's keyboard "Done" action
        editPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin(view);
                return true;
            }
            return false;
        });

        // Observe login state
        viewModel.getLoginState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            switch (state.status) {
                case LOADING:
                    btnLogin.setEnabled(false);
                    textError.setVisibility(View.GONE);
                    if (progressLogin != null) {
                        progressLogin.setVisibility(View.VISIBLE);
                    }
                    break;

                case SUCCESS:
                    if (progressLogin != null) {
                        progressLogin.setVisibility(View.GONE);
                    }
                    navigateForRole(view, state.role);
                    break;

                case ERROR:
                    btnLogin.setEnabled(true);
                    if (progressLogin != null) {
                        progressLogin.setVisibility(View.GONE);
                    }
                    textError.setVisibility(View.VISIBLE);
                    textError.setText(state.error);
                    break;

                default:
                    // IDLE — nothing to do
                    break;
            }
        });
    }

    private void attemptLogin(@NonNull View view) {
        String username = editUsername.getText() != null
                ? editUsername.getText().toString().trim() : "";
        String password = editPassword.getText() != null
                ? editPassword.getText().toString() : "";
        viewModel.login(username, password);
    }

    private void navigateForRole(@NonNull View view, @Nullable String role) {
        if (role == null) return;

        // Read session values from the ViewModel's session manager
        SessionManager sm = viewModel.getSessionManager();
        String orgId  = sm.getOrgId()  != null ? sm.getOrgId()  : "";
        String userId = sm.getUserId() != null ? sm.getUserId() : "";

        int actionId;
        Bundle args = new Bundle();

        switch (role) {
            case "ADMIN":
                actionId = R.id.action_login_to_admin;
                // Admin dashboard doesn't require args — navigate with no bundle
                Navigation.findNavController(view).navigate(actionId);
                return;
            case "DISPATCHER":
                actionId = R.id.action_login_to_dispatcher;
                args.putString("org_id", orgId);
                args.putString("user_id", userId);
                break;
            case "WORKER":
                actionId = R.id.action_login_to_worker;
                args.putString("org_id", orgId);
                args.putString("user_id", userId);
                break;
            case "COMPLIANCE_REVIEWER":
                actionId = R.id.action_login_to_compliance;
                args.putString("org_id", orgId);
                args.putString("reviewer_id", userId);
                break;
            default:
                // Unknown role — stay on login and show an error
                textError.setVisibility(View.VISIBLE);
                textError.setText("Unrecognised role: " + role);
                btnLogin.setEnabled(true);
                return;
        }

        Navigation.findNavController(view).navigate(actionId, args);
    }
}
