package com.estimote.sdk.connection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.Utils;
import com.estimote.sdk.internal.HashCode;
import com.estimote.sdk.internal.Objects;
import com.estimote.sdk.internal.Objects.ToStringHelper;
import com.estimote.sdk.internal.UnsignedInteger;
import com.estimote.sdk.utils.AuthMath;
import com.estimote.sdk.utils.L;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BeaconConnection
{
	public static Set<Integer> ALLOWED_POWER_LEVELS = Collections.unmodifiableSet(new HashSet(Arrays.asList(new Integer[] { Integer.valueOf(-30), Integer.valueOf(-20), Integer.valueOf(-16), Integer.valueOf(-12), Integer.valueOf(-8), Integer.valueOf(-4), Integer.valueOf(0), Integer.valueOf(4) })));
	private final Context context;
	private final BluetoothDevice device;
	private final ConnectionCallback connectionCallback;
	private final Handler handler;
	private final BluetoothGattCallback bluetoothGattCallback;
	private final Runnable timeoutHandler;
	private final AuthService authService;
	private final EstimoteService estimoteService;
	private final VersionService versionService;
	private final Map<UUID, BluetoothService> uuidToService;
	private boolean didReadCharacteristics;
	private LinkedList<BluetoothGattCharacteristic> toFetch;
	private long aAuth;
	private long bAuth;
	private BluetoothGatt bluetoothGatt;

	public BeaconConnection(Context context, Beacon beacon, ConnectionCallback connectionCallback)
	{
		this.context = context;
		this.device = deviceFromBeacon(beacon);
		this.toFetch = new LinkedList();
		this.handler = new Handler();
		this.connectionCallback = connectionCallback;
		this.bluetoothGattCallback = createBluetoothGattCallback();
		this.timeoutHandler = createTimeoutHandler();
		this.authService = new AuthService();
		this.estimoteService = new EstimoteService();
		this.versionService = new VersionService();
		this.uuidToService = new HashMap()
		{
		
		};
	}

	private BluetoothDevice deviceFromBeacon(Beacon beacon)
	{
		BluetoothManager bluetoothManager = (BluetoothManager)this.context.getSystemService("bluetooth");
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		return bluetoothAdapter.getRemoteDevice(beacon.getMacAddress());
	}

	public void authenticate()
	{
		L.d("Trying to connect to GATT");
		this.didReadCharacteristics = false;
		this.bluetoothGatt = this.device.connectGatt(this.context, false, this.bluetoothGattCallback);
		this.handler.postDelayed(this.timeoutHandler, TimeUnit.SECONDS.toMillis(10L));
	}

	public void close()
	{
		if (this.bluetoothGatt != null) {
			this.bluetoothGatt.disconnect();
			this.bluetoothGatt.close();
		}
		this.handler.removeCallbacks(this.timeoutHandler);
	}

	public boolean isConnected()
	{
		BluetoothManager bluetoothManager = (BluetoothManager)this.context.getSystemService("bluetooth");
		int connectionState = bluetoothManager.getConnectionState(this.device, 7);
		return (connectionState == 2) && (this.didReadCharacteristics);
	}

	public void writeProximityUuid(String proximityUuid, WriteCallback writeCallback)
	{
		if ((!isConnected()) || (!this.estimoteService.hasCharacteristic(EstimoteUuid.UUID_NORMAL_CHAR))) {
			L.w("Not connected to beacon. Discarding changing proximity UUID.");
			writeCallback.onError();
			return;
		}
		
		byte[] uuidAsBytes = HashCode.fromString(proximityUuid.replaceAll("-", "").toLowerCase()).asBytes();
		BluetoothGattCharacteristic uuidChar = this.estimoteService.beforeCharacteristicWrite(EstimoteUuid.UUID_NORMAL_CHAR, writeCallback);

		uuidChar.setValue(uuidAsBytes);
		this.bluetoothGatt.writeCharacteristic(uuidChar);
	}

	public void writeAdvertisingInterval(int intervalMillis, WriteCallback writeCallback)
	{
		if ((!isConnected()) || (!this.estimoteService.hasCharacteristic(EstimoteUuid.ADVERTISING_INTERVAL_CHAR))) {
			L.w("Not connected to beacon. Discarding changing advertising interval.");
			writeCallback.onError();
			return;
		}
		
		intervalMillis = Math.max(0, Math.min(2000, intervalMillis));
		int correctedInterval = (int)(intervalMillis / 0.625D);
		BluetoothGattCharacteristic intervalChar = this.estimoteService.beforeCharacteristicWrite(EstimoteUuid.ADVERTISING_INTERVAL_CHAR, writeCallback);

		intervalChar.setValue(correctedInterval, 18, 0);
		this.bluetoothGatt.writeCharacteristic(intervalChar);
	}

	public void writeBroadcastingPower(int powerDBM, WriteCallback writeCallback)
	{
		if ((!isConnected()) || (!this.estimoteService.hasCharacteristic(EstimoteUuid.POWER_CHAR))) {
			L.w("Not connected to beacon. Discarding changing broadcasting power.");
			writeCallback.onError();
			return;
		}
		
		if (!ALLOWED_POWER_LEVELS.contains(Integer.valueOf(powerDBM))) {
			L.w("Not allowed power level. Discarding changing broadcasting power.");
			writeCallback.onError();
			return;
		}
		
		BluetoothGattCharacteristic powerChar = this.estimoteService.beforeCharacteristicWrite(EstimoteUuid.POWER_CHAR, writeCallback);

		powerChar.setValue(powerDBM, 17, 0);
		this.bluetoothGatt.writeCharacteristic(powerChar);
	}

	public void writeMajor(int major, WriteCallback writeCallback)
	{
		if (!isConnected()) {
			L.w("Not connected to beacon. Discarding changing major.");
			writeCallback.onError();
			return;
		}
		
		major = Utils.normalize16BitUnsignedInt(major);
		BluetoothGattCharacteristic majorChar = this.estimoteService.beforeCharacteristicWrite(EstimoteUuid.MAJOR_CHAR, writeCallback);

		majorChar.setValue(major, 18, 0);
		this.bluetoothGatt.writeCharacteristic(majorChar);
	}

	public void writeMinor(int minor, WriteCallback writeCallback)
	{
		if (!isConnected()) {
			L.w("Not connected to beacon. Discarding changing minor.");
			writeCallback.onError();
			return;
		}
		minor = Utils.normalize16BitUnsignedInt(minor);
		BluetoothGattCharacteristic minorChar = this.estimoteService.beforeCharacteristicWrite(EstimoteUuid.MINOR_CHAR, writeCallback);
		
		minorChar.setValue(minor, 18, 0);
		this.bluetoothGatt.writeCharacteristic(minorChar);
	}

	private Runnable createTimeoutHandler() {
		return new Runnable()
		{
			public void run() {
				L.v("Timeout while authenticating");
				if (!BeaconConnection.this.didReadCharacteristics) {
					if (BeaconConnection.this.bluetoothGatt != null) {
						BeaconConnection.this.bluetoothGatt.disconnect();
						BeaconConnection.this.bluetoothGatt.close();
						BeaconConnection.this.bluetoothGatt = null;
					}
					BeaconConnection.this.notifyAuthenticationError();
				}
			}
		};
	}

	private BluetoothGattCallback createBluetoothGattCallback() {
		return new BluetoothGattCallback()
		{
			public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
				if (newState == 2) {
					L.d("Connected to GATT server, discovering services: " + gatt.discoverServices());
				} else if ((newState == 0) && (!BeaconConnection.this.didReadCharacteristics)) {
					L.w("Disconnected from GATT server");
					BeaconConnection.this.notifyAuthenticationError();
				} else if (newState == 0) {
					L.w("Disconnected from GATT server");
					BeaconConnection.this.notifyDisconnected();
				}
			}

			public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
			{
				if (BeaconConnection.this.authService.isAuthSeedCharacteristic(characteristic)) {
					if (status == 0) {
						BeaconConnection.this.onBeaconSeedResponse(gatt, characteristic);
					} else {
						L.w("Auth failed: could not read beacon's response to seed");
						BeaconConnection.this.notifyAuthenticationError();
					}
					return;
				}

				if (status == 0) {
					((BluetoothService)BeaconConnection.this.uuidToService.get(characteristic.getService().getUuid())).update(characteristic);
					BeaconConnection.this.readCharacteristics(gatt);
				} else {
					L.w("Failed to read characteristic");
					BeaconConnection.this.toFetch.clear();
					BeaconConnection.this.notifyAuthenticationError();
				}
			}
	

			public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
			{
				if (BeaconConnection.this.authService.isAuthSeedCharacteristic(characteristic)) {
					if (status == 0) {
						BeaconConnection.this.onSeedWriteCompleted(gatt, characteristic);
					} else {
						L.w("Authentication failed: seed not negotiated");
						BeaconConnection.this.notifyAuthenticationError();
					}
					
				} else if (BeaconConnection.this.authService.isAuthVectorCharacteristic(characteristic)) {
					
					if (status == 0) {
						BeaconConnection.this.onAuthenticationCompleted(gatt);
					} else {
						L.w("Authentication failed: auth source write failed");
						BeaconConnection.this.notifyAuthenticationError();
					}
				} else if (EstimoteUuid.ESTIMOTE_SERVICE.equals(characteristic.getService().getUuid()))
					
					BeaconConnection.this.estimoteService.onCharacteristicWrite(characteristic, status);
			}

			public void onServicesDiscovered(BluetoothGatt gatt, int status)
			{
				if (status == 0) {
					L.v("Services discovered");
					BeaconConnection.this.processDiscoveredServices(gatt.getServices());
					BeaconConnection.this.startAuthentication(gatt);
				} else {
					L.w("Could not discover services, status: " + status);
					BeaconConnection.this.notifyAuthenticationError();
				}
			}
		};
	}

	private void notifyAuthenticationError() {
		this.handler.removeCallbacks(this.timeoutHandler);
		this.connectionCallback.onAuthenticationError();
	}

	private void notifyDisconnected() {
		this.connectionCallback.onDisconnected();
	}

	private void processDiscoveredServices(List<BluetoothGattService> services) {
		this.authService.processGattServices(services);
		this.estimoteService.processGattServices(services);
		this.versionService.processGattServices(services);
		this.toFetch.clear();
		this.toFetch.addAll(this.estimoteService.getAvailableCharacteristics());
		this.toFetch.addAll(this.versionService.getAvailableCharacteristics());
	}

	private void startAuthentication(BluetoothGatt gatt)
	{
		if (!this.authService.isAvailable()) {
			L.w("Authentication service is not available on the beacon");
			notifyAuthenticationError();
			return;
		}
		this.aAuth = AuthMath.randomUnsignedInt();
		BluetoothGattCharacteristic seedChar = this.authService.getAuthSeedCharacteristic();
		seedChar.setValue(AuthMath.firstStepSecret(this.aAuth), 20, 0);
		gatt.writeCharacteristic(seedChar);
	}

	private void onSeedWriteCompleted(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic)
	{
		this.handler.postDelayed(new Runnable()
		{
			public void run() {
				gatt.readCharacteristic(characteristic);
			}
		}
		, 500L);
	}

	private void onBeaconSeedResponse(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
	{
		Integer intValue = characteristic.getIntValue(20, 0);
		this.bAuth = UnsignedInteger.fromIntBits(intValue.intValue()).longValue();
		String macAddress = this.device.getAddress().replace(":", "");
		BluetoothGattCharacteristic vectorChar = this.authService.getAuthVectorCharacteristic();
		vectorChar.setValue(AuthMath.secondStepSecret(this.aAuth, this.bAuth, macAddress));
		gatt.writeCharacteristic(vectorChar);
	}

	private void onAuthenticationCompleted(final BluetoothGatt gatt)
	{
		this.handler.postDelayed(new Runnable()
		{
			public void run() {
				BeaconConnection.this.readCharacteristics(gatt);
			}
		}
		, 500L);
	}

	private void readCharacteristics(BluetoothGatt gatt)
	{
		if (!this.toFetch.isEmpty())
			gatt.readCharacteristic((BluetoothGattCharacteristic)this.toFetch.poll());
		else if (this.bluetoothGatt != null)
			onAuthenticated();
	}

	private void onAuthenticated()
	{
		L.v("Authenticated to beacon");
		this.handler.removeCallbacks(this.timeoutHandler);
		this.didReadCharacteristics = true;
		this.connectionCallback.onAuthenticated(new BeaconCharacteristics(this.estimoteService, this.versionService));
	}

	public static abstract interface WriteCallback
	{
		public abstract void onSuccess();
		public abstract void onError();
	}

	public static abstract interface ConnectionCallback
	{
		public abstract void onAuthenticated(BeaconConnection.BeaconCharacteristics paramBeaconCharacteristics);
		public abstract void onAuthenticationError();
		public abstract void onDisconnected();
	}

	public static class BeaconCharacteristics
	{
		private final Integer batteryPercent;
		private final Byte broadcastingPower;
		private final Integer advertisingIntervalMillis;
		private final String softwareVersion;
		private final String hardwareVersion;

		public BeaconCharacteristics(EstimoteService estimoteService, VersionService versionService)
		{
			this.broadcastingPower = estimoteService.getPowerDBM();
			this.batteryPercent = estimoteService.getBatteryPercent();
			this.advertisingIntervalMillis = estimoteService.getAdvertisingIntervalMillis();
			this.softwareVersion = versionService.getSoftwareVersion();
			this.hardwareVersion = versionService.getHardwareVersion();
		}

		public Integer getBatteryPercent() {
			return this.batteryPercent;
		}
		
		public Byte getBroadcastingPower() {
			return this.broadcastingPower;
		}
		
		public Integer getAdvertisingIntervalMillis() {
			return this.advertisingIntervalMillis;
		}
		
		public String getSoftwareVersion() {
			return this.softwareVersion;
		}
		
		public String getHardwareVersion() {
			return this.hardwareVersion;
		}

		public String toString() {
			return Objects.toStringHelper(this).add("batteryPercent", this.batteryPercent).add("broadcastingPower", this.broadcastingPower).add("advertisingIntervalMillis", this.advertisingIntervalMillis).add("softwareVersion", this.softwareVersion).add("hardwareVersion", this.hardwareVersion).toString();
		}
	}
}

