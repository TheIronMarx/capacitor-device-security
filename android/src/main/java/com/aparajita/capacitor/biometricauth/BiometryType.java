package com.aparajita.capacitor.biometricauth;

public enum BiometryType {
  NONE(0),
  FINGERPRINT(3),
  FACE(4),
  IRIS(5);

  private final int type;

  BiometryType(int type) {
    this.type = type;
  }

  public int getType() {
    return this.type;
  }
}
