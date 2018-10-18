package app.gotogether;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ErrorCodes;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseUserMetadata;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MultipleLoginActivity extends AppCompatActivity {

    private static final String TAG = "MultipleLoginActivity";
    private static final int RC_SIGN_IN = 123;
    private static final String GOOGLE_TOS_URL = "https://www.google.com/policies/terms/";
    private FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = FirebaseFirestore.getInstance();
        // required settings
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build();
        db.setFirestoreSettings(settings);

        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }else {
            showSignInScreen();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            handleSignInResponse(resultCode, data);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (auth.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    private void handleSignInResponse(int resultCode, @Nullable Intent data) {
        IdpResponse response = IdpResponse.fromResultIntent(data);

        // Successfully signed in
        if (resultCode == RESULT_OK) {

            Log.i(TAG, "User signed in!");
            // determine if new or returning user by comparing creation and last login
            FirebaseUserMetadata metadata = auth.getCurrentUser().getMetadata();
            if (metadata.getCreationTimestamp() == metadata.getLastSignInTimestamp()) {
                // The user is new, add to database
                Log.i(TAG, "... and it's a new user!");
                // Retrieve user info
                FirebaseUser fbUser = auth.getCurrentUser();
                // Name, email address, and profile photo Url
                String name = fbUser.getDisplayName();
                String email = fbUser.getEmail();
                Uri photoUrl = fbUser.getPhotoUrl();
                // The user's ID, unique to the Firebase project. Do NOT use this value to
                String uid = fbUser.getUid();

                // Add to DB
                Map<String, Object> user = new HashMap<>();
                user.put("usernasme", name);
                user.put("events", new ArrayList<>());
                db.collection("users").document(uid)
                        .set(user)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Log.i(TAG, "User added to the db with uid: "+uid);
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.w(TAG, "Error writing document to db", e);
                            }
                        });
            }

            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            // Sign in failed
            if (response == null) {
                // User pressed back button
                showSnackbar(R.string.sign_in_cancelled);
                return;
            }

            if (response.getError().getErrorCode() == ErrorCodes.NO_NETWORK) {
                showSnackbar(R.string.no_internet_connection);
                return;
            }

            showSnackbar(R.string.unknown_error);
            Log.e(TAG, "Sign-in error: ", response.getError());
        }
    }

    private void showSignInScreen() {
        startActivityForResult(
                AuthUI.getInstance().createSignInIntentBuilder()
                        .setTheme(R.style.GoTogether_Theme)
                        .setLogo(R.drawable.ic_launcher_foreground)
                        .setAvailableProviders(getSelectedProviders())
                        /*.setTosAndPrivacyPolicyUrls(getSelectedTosUrl(),
                                getSelectedPrivacyPolicyUrl())*/
                        .setIsSmartLockEnabled(true,
                                true)
                        .build(),
                RC_SIGN_IN);
    }

    private List<AuthUI.IdpConfig> getSelectedProviders() {
        List<AuthUI.IdpConfig> selectedProviders = new ArrayList<>();

            selectedProviders.add(
                    new AuthUI.IdpConfig.GoogleBuilder().build());

            selectedProviders.add(new AuthUI.IdpConfig.EmailBuilder()
                    .setRequireName(true)
                    .setAllowNewAccounts(true)
                    .build());

            selectedProviders.add(new AuthUI.IdpConfig.AnonymousBuilder().build());

        return selectedProviders;
    }

    private void showSnackbar(@StringRes int errorMessageRes) {
        Snackbar.make(findViewById(android.R.id.content), errorMessageRes, Snackbar.LENGTH_LONG).show();
    }

}