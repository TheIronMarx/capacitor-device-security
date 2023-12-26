package com.aparajita.capacitor.biometricauth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

import java.util.concurrent.Executor;

public class AuthActivity extends AppCompatActivity {

  @SuppressLint("WrongConstant")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_auth_activity);

    BiometricPrompt.PromptInfo promptInfo = getPromptInfoBuilder(getIntent());
    BiometricPrompt prompt = getBiometricPrompt();
    prompt.authenticate(promptInfo);
  }

  private BiometricPrompt getBiometricPrompt() {
    return new BiometricPrompt(
      this,
      getExecutor(),
      new BiometricPrompt.AuthenticationCallback() {
        @Override
        public void onAuthenticationError(int errorCode, @NonNull CharSequence errorMessage) {
          super.onAuthenticationError(errorCode, errorMessage);
          finishActivity(BiometryResultType.ERROR, errorCode, (String) errorMessage);
        }

        @Override
        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
          super.onAuthenticationSucceeded(result);
          finishActivity();
        }
      }
    );
  }

  private Executor getExecutor() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return this.getMainExecutor();
    } else {
      return command -> new Handler(this.getMainLooper()).post(command);
    }
  }

  // TODO: 12/21/23 Caller should make only call this if KeyguardManager.isDeviceSecure == true
  private BiometricPrompt.PromptInfo getPromptInfoBuilder(Intent intent) {
    BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder();
    String title = intent.getStringExtra(BiometricAuthNative.PARAMETER_TITLE);
    String subtitle = intent.getStringExtra(BiometricAuthNative.PARAMETER_SUBTITLE);
    String description = intent.getStringExtra(BiometricAuthNative.PARAMETER_REASON);

    // The title must be non-null and non-empty // TODO: 12/21/23 Enforce title on API maybe?
    if (title == null || title.isEmpty()) {
      // TODO: 12/26/23 Good default name?
      title = "Authenticate";
    }

    builder.setTitle(title).setSubtitle(subtitle).setDescription(description);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
    } else {
      builder.setDeviceCredentialAllowed(true);
    }

    builder.setConfirmationRequired(intent.getBooleanExtra(BiometricAuthNative.CONFIRMATION_REQUIRED, true));

    return builder.build();
  }

  private void finishActivity() {
    finishActivity(BiometryResultType.SUCCESS, 0, "");
  }

  private void finishActivity(BiometryResultType resultType, int errorCode, String errorMessage) {
    Intent intent = new Intent();
    String prefix = BiometricAuthNative.RESULT_EXTRA_PREFIX;

    intent
      .putExtra(prefix + BiometricAuthNative.RESULT_TYPE, resultType.toString())
      .putExtra(prefix + BiometricAuthNative.RESULT_ERROR_CODE, errorCode)
      .putExtra(prefix + BiometricAuthNative.RESULT_ERROR_MESSAGE, errorMessage);

    setResult(RESULT_OK, intent);
    finish();
  }
}
