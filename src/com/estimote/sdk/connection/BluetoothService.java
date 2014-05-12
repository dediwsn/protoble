package com.estimote.sdk.connection;

import android.bluetooth.BluetoothGattCharacteristic;

public abstract interface BluetoothService
{
  public abstract void update(BluetoothGattCharacteristic paramBluetoothGattCharacteristic);
}

