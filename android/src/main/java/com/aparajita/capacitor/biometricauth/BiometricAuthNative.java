package com.aparajita.capacitor.biometricauth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResult;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;
import java.util.HashMap;

@SuppressLint("RestrictedApi")
@CapacitorPlugin(name = "BiometricAuthNative")
// TODO: 12/21/23 I think this can be renamed
public class BiometricAuthNative extends Plugin {

  public static final String RESULT_TYPE = "type";
  public static final String RESULT_ERROR_CODE = "errorCode";
  public static final String RESULT_ERROR_MESSAGE = "errorMessage";

  public static final String PARAMETER_TITLE = "androidTitle";
  public static final String PARAMETER_SUBTITLE = "androidSubtitle";
  public static final String PARAMETER_REASON = "reason";
  public static final String PARAMETER_CANCEL_TITLE = "cancelTitle";

  // TODO: 12/21/23 Remove allowDeviceCredential from parameters in definitions.d
  //  public static final String PARAMETER_DEVICE_CREDENTIAL = "allowDeviceCredential";

  public static final String CONFIRMATION_REQUIRED = "androidConfirmationRequired";
  public static final String MAX_ATTEMPTS = "androidMaxAttempts";
  public static final int DEFAULT_MAX_ATTEMPTS = 3;
  // Error code when biometry is not recognized
  public static final String BIOMETRIC_FAILURE = "authenticationFailed";
  // Maps biometry error numbers to string error codes
  private static final HashMap<Integer, String> biometryErrorCodeMap;
  private static final HashMap<BiometryType, String> biometryNameMap;
  private static final String INVALID_CONTEXT_ERROR = "invalidContext";
  public static String RESULT_EXTRA_PREFIX;

  static {
    biometryErrorCodeMap = new HashMap<>();
    biometryErrorCodeMap.put(BiometricManager.BIOMETRIC_SUCCESS, "");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_CANCELED, "systemCancel");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_HW_NOT_PRESENT, "biometryNotAvailable");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_HW_UNAVAILABLE, "biometryNotAvailable");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_LOCKOUT, "biometryLockout");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_LOCKOUT_PERMANENT, "biometryLockout");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_NEGATIVE_BUTTON, "userCancel");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_NO_BIOMETRICS, "biometryNotEnrolled");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL, "noDeviceCredential");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_NO_SPACE, "systemCancel");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_TIMEOUT, "systemCancel");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, "systemCancel");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_USER_CANCELED, "userCancel");
    biometryErrorCodeMap.put(BiometricPrompt.ERROR_VENDOR, "systemCancel");
  }

  static {
    biometryNameMap = new HashMap<>();
    biometryNameMap.put(BiometryType.NONE, "No Authentication");
    biometryNameMap.put(BiometryType.FINGERPRINT, "Fingerprint Authentication");
    biometryNameMap.put(BiometryType.FACE, "Face Authentication");
    biometryNameMap.put(BiometryType.IRIS, "Iris Authentication");
  }

  private ArrayList<BiometryType> biometryTypes;

  /**
   * Check the device's availability and type of biometric authentication.
   * // TODO: 12/20/23 Rename to something better
   */
  @PluginMethod
  public void checkBiometry(PluginCall call) {
    call.resolve(checkDeviceSecurity());
  }

  /**
   * Check the device's availability and type of biometric authentication.
   */
  private JSObject checkDeviceSecurity() {
    BiometricManager manager = BiometricManager.from(getContext());

    int biometryResult;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      biometryResult = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
    } else {
      biometryResult = manager.canAuthenticate();
    }

    JSObject result = new JSObject();
    result.put("isAvailable", biometryResult == BiometricManager.BIOMETRIC_SUCCESS);

    biometryTypes = getDeviceBiometryTypes();
    result.put("biometryType", biometryTypes.get(0).getType());

    JSArray returnTypes = new JSArray();

    for (BiometryType type : biometryTypes) {
      returnTypes.put(type.getType());
    }

    result.put("biometryTypes", returnTypes);

    String reason = buildReasoning(biometryResult);

    String errorCode = biometryErrorCodeMap.get(biometryResult);

    if (errorCode == null) {
      errorCode = "biometryNotAvailable";
    }

    result.put("reason", reason);
    result.put("code", errorCode);
    return result;
  }

  private ArrayList<BiometryType> getDeviceBiometryTypes() {
    ArrayList<BiometryType> types = new ArrayList<>();
    PackageManager manager = getContext().getPackageManager();

    if (manager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
      types.add(BiometryType.FINGERPRINT);
    }

    if (manager.hasSystemFeature(PackageManager.FEATURE_FACE)) {
      types.add(BiometryType.FACE);
    }

    if (manager.hasSystemFeature(PackageManager.FEATURE_IRIS)) {
      types.add(BiometryType.IRIS);
    }

    if (types.size() == 0) {
      types.add(BiometryType.NONE);
    }

    return types;
  }

  /**
   * Prompt the user for biometric authentication. Ensure the user has device security enrolled first.
   */
  @PluginMethod
  public void internalAuthenticate(final PluginCall call) {
    // The result of an intent is supposed to have the package name as a prefix
    RESULT_EXTRA_PREFIX = getContext().getPackageName() + ".";

    // TODO: 12/21/23 if (!KeyguardManager.isDeviceSecure) {return not enrolled error}. Shouldn't checkDeviceSecurity also do this for SDK 29 and below?

    Intent intent = new Intent(getContext(), AuthActivity.class);

    // Pass the options to the activity
    intent.putExtra(PARAMETER_TITLE, call.getString(PARAMETER_TITLE, biometryNameMap.get(biometryTypes.get(0))));
    intent.putExtra(PARAMETER_SUBTITLE, call.getString(PARAMETER_SUBTITLE));
    intent.putExtra(PARAMETER_REASON, call.getString(PARAMETER_REASON));
    intent.putExtra(PARAMETER_CANCEL_TITLE, call.getString(PARAMETER_CANCEL_TITLE));
    //    intent.putExtra(PARAMETER_DEVICE_CREDENTIAL, call.getBoolean(PARAMETER_DEVICE_CREDENTIAL, false));

    if (call.hasOption(CONFIRMATION_REQUIRED)) {
      intent.putExtra(CONFIRMATION_REQUIRED, call.getBoolean(CONFIRMATION_REQUIRED, true));
    }

    // Just in case the developer does something dumb like using a number < 1...
    Integer maxAttemptsConfig = call.getInt(MAX_ATTEMPTS, DEFAULT_MAX_ATTEMPTS);
    int maxAttempts = Math.max(maxAttemptsConfig == null ? 0 : maxAttemptsConfig, 1);
    intent.putExtra(MAX_ATTEMPTS, maxAttempts);

    startActivityForResult(call, intent, "authenticateResult");
  }

  @ActivityCallback
  protected void authenticateResult(PluginCall call, ActivityResult result) {
    int resultCode = result.getResultCode();

    // If the system canceled the activity, we might get RESULT_CANCELED in resultCode.
    // In that case return that immediately, because there won't be any data.
    if (resultCode == Activity.RESULT_CANCELED) {
      call.reject("The system canceled authentication", biometryErrorCodeMap.get(BiometricPrompt.ERROR_CANCELED));
      return;
    }

    // Convert the string result type to an enum
    Intent data = result.getData();
    String resultTypeName = null;

    if (data != null) {
      resultTypeName = data.getStringExtra(RESULT_EXTRA_PREFIX + BiometricAuthNative.RESULT_TYPE);
    }

    if (resultTypeName == null) {
      call.reject("Missing data in the result of the activity", INVALID_CONTEXT_ERROR);
      return;
    }

    BiometryResultType resultType;

    try {
      resultType = BiometryResultType.valueOf(resultTypeName);
    } catch (IllegalArgumentException e) {
      call.reject("Invalid data in the result of the activity", INVALID_CONTEXT_ERROR);
      return;
    }

    int errorCode = data.getIntExtra(RESULT_EXTRA_PREFIX + BiometricAuthNative.RESULT_ERROR_CODE, 0);
    String errorMessage = data.getStringExtra(RESULT_EXTRA_PREFIX + BiometricAuthNative.RESULT_ERROR_MESSAGE);

    switch (resultType) {
      case SUCCESS -> call.resolve();
      // Biometry was successfully presented but was not recognized
      case FAILURE -> call.reject(errorMessage, BIOMETRIC_FAILURE);
      // The user cancelled, the system cancelled, or some error occurred.
      // If the user cancelled, errorMessage is the text of the "negative" button (wtf)
      case ERROR -> {
        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
          errorMessage = "Cancel button was pressed";
        }

        call.reject(errorMessage, biometryErrorCodeMap.get(errorCode));
      }
    }
  }

  private String buildReasoning(int biometryResult) {
    switch (biometryResult) {
      case BiometricManager.BIOMETRIC_SUCCESS:
        break;
      case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
        return "Biometry hardware is present, but currently unavailable.";
      case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
        return "The user does not have any biometrics enrolled.";
      case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
        return "There is no biometric hardware on this device.";
      case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
        return "The user can’t authenticate because a security vulnerability has been discovered with one or more hardware sensors.";
      case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
        return "The user can’t authenticate because the specified options are incompatible with the current Android version.";
      case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
        return "Unable to determine whether the user can authenticate.";
    }
    return "";
  }
}
