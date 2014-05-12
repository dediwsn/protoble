package com.estimote.sdk.connection;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class EstimoteService
implements BluetoothService
{
	private final HashMap<UUID, BluetoothGattCharacteristic> characteristics = new HashMap();
	private final HashMap<UUID, BeaconConnection.WriteCallback> writeCallbacks = new HashMap();

	public void processGattServices(List<BluetoothGattService> services)
	{
		for (BluetoothGattService service : services)
			if (EstimoteUuid.ESTIMOTE_SERVICE.equals(service.getUuid())) {
				this.characteristics.put(EstimoteUuid.UUID_NORMAL_CHAR, service.getCharacteristic(EstimoteUuid.UUID_NORMAL_CHAR));
				this.characteristics.put(EstimoteUuid.MAJOR_CHAR, service.getCharacteristic(EstimoteUuid.MAJOR_CHAR));
				this.characteristics.put(EstimoteUuid.MINOR_CHAR, service.getCharacteristic(EstimoteUuid.MINOR_CHAR));
				this.characteristics.put(EstimoteUuid.BATTERY_CHAR, service.getCharacteristic(EstimoteUuid.BATTERY_CHAR));
				this.characteristics.put(EstimoteUuid.POWER_CHAR, service.getCharacteristic(EstimoteUuid.POWER_CHAR));
				this.characteristics.put(EstimoteUuid.ADVERTISING_INTERVAL_CHAR, service.getCharacteristic(EstimoteUuid.ADVERTISING_INTERVAL_CHAR));
			}
	}

	public boolean hasCharacteristic(UUID uuid)
	{
		return this.characteristics.containsKey(uuid);
	}

	public Integer getBatteryPercent() {
		return this.characteristics.containsKey(EstimoteUuid.BATTERY_CHAR) ? Integer.valueOf(getUnsignedByte(((BluetoothGattCharacteristic)this.characteristics.get(EstimoteUuid.BATTERY_CHAR)).getValue())) : null;
	}

	public Byte getPowerDBM()
	{
		return this.characteristics.containsKey(EstimoteUuid.POWER_CHAR) ? Byte.valueOf(((BluetoothGattCharacteristic)this.characteristics.get(EstimoteUuid.POWER_CHAR)).getValue()[0]) : null;
	}

	public Integer getAdvertisingIntervalMillis()
	{
		return this.characteristics.containsKey(EstimoteUuid.ADVERTISING_INTERVAL_CHAR) ? Integer.valueOf(Math.round(getUnsignedInt16(((BluetoothGattCharacteristic)this.characteristics.get(EstimoteUuid.ADVERTISING_INTERVAL_CHAR)).getValue()) * 0.625F)) : null;
	}

	public void update(BluetoothGattCharacteristic characteristic)
	{
		this.characteristics.put(characteristic.getUuid(), characteristic);
	}

	public Collection<BluetoothGattCharacteristic> getAvailableCharacteristics() {
		List chars = new ArrayList(this.characteristics.values());
		chars.removeAll(Collections.singleton(null));
		return chars;
	}

	private static int getUnsignedByte(byte[] bytes) {
		return unsignedByteToInt(bytes[0]);
	}

	private static int getUnsignedInt16(byte[] bytes) {
		return unsignedByteToInt(bytes[0]) + (unsignedByteToInt(bytes[1]) << 8);
	}

	public BluetoothGattCharacteristic beforeCharacteristicWrite(UUID uuid, BeaconConnection.WriteCallback callback) {
		this.writeCallbacks.put(uuid, callback);
		return (BluetoothGattCharacteristic)this.characteristics.get(uuid);
	}

	public void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
		BeaconConnection.WriteCallback writeCallback = (BeaconConnection.WriteCallback)this.writeCallbacks.remove(characteristic.getUuid());
		if (status == 0)
			writeCallback.onSuccess();
		else
			writeCallback.onError();
	}

	private static int unsignedByteToInt(byte value)
	{
		return value & 0xFF;
	}

}

