package com.estimote.sdk.connection;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class AuthService
implements BluetoothService
{
	private final HashMap<UUID, BluetoothGattCharacteristic> characteristics = new HashMap();

	public void processGattServices(List<BluetoothGattService> services)
	{
		for (BluetoothGattService service : services)
			if (EstimoteUuid.AUTH_SERVICE.equals(service.getUuid())) {
				this.characteristics.put(EstimoteUuid.AUTH_SEED_CHAR, service.getCharacteristic(EstimoteUuid.AUTH_SEED_CHAR));
				this.characteristics.put(EstimoteUuid.AUTH_VECTOR_CHAR, service.getCharacteristic(EstimoteUuid.AUTH_VECTOR_CHAR));
			}
	}

	public void update(BluetoothGattCharacteristic characteristic)
	{
		this.characteristics.put(characteristic.getUuid(), characteristic);
	}

	public boolean isAvailable()
	{
		return this.characteristics.size() == 2;
	}

	public boolean isAuthSeedCharacteristic(BluetoothGattCharacteristic characteristic) {
		return characteristic.getUuid().equals(EstimoteUuid.AUTH_SEED_CHAR);
	}

	public boolean isAuthVectorCharacteristic(BluetoothGattCharacteristic characteristic) {
		return characteristic.getUuid().equals(EstimoteUuid.AUTH_VECTOR_CHAR);
	}

	public BluetoothGattCharacteristic getAuthSeedCharacteristic() {
		return (BluetoothGattCharacteristic)this.characteristics.get(EstimoteUuid.AUTH_SEED_CHAR);
	}

	public BluetoothGattCharacteristic getAuthVectorCharacteristic() {
		return (BluetoothGattCharacteristic)this.characteristics.get(EstimoteUuid.AUTH_VECTOR_CHAR);
	}
}

