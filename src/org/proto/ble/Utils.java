package org.proto.ble;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;

public class Utils {
	
	public static final String DEVICE_NAME = "iBeacon RedBear";
	  

	public static final UUID BLESHIELD_SERVICE = UUID.fromString("B8E06067-62AD-41BA-9231-206AE80AB550");
	public static final UUID BLESHIELD_ADDRESS = UUID.fromString("65C228DA-BAD1-4F41-B55F-3D177F4E2196");
	public static final UUID BLESHIELD_READ = UUID.fromString("F897177B-AEE8-4767-8ECC-CC694FD5FCEE");
	public static final UUID BLESHIELD_WRITE = UUID.fromString("BF45E40A-DE2A-4BC8-BBA0-E5D6065F1B4B");
	public static final UUID BLESHIELD_NOTIFYCONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	
    public static Map<String, BluetoothDevice> mDevices = new HashMap<String, BluetoothDevice>();

	
	public static ArrayList<String> deviceName = new ArrayList<String>();
	public static ArrayList<String> deviceAddress = new ArrayList<String>();
}
