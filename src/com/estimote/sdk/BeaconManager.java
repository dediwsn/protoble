package com.estimote.sdk;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import com.estimote.sdk.internal.Preconditions;
import com.estimote.sdk.service.BeaconService;
import com.estimote.sdk.service.MonitoringResult;
import com.estimote.sdk.service.RangingResult;
import com.estimote.sdk.service.ScanPeriodData;
import com.estimote.sdk.utils.L;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class BeaconManager
{
	private static final String ANDROID_MANIFEST_CONDITIONS_MSG = "AndroidManifest.xml does not contain android.permission.BLUETOOTH or android.permission.BLUETOOTH_ADMIN permissions. BeaconService may be also not declared in AndroidManifest.xml.";
	private final Context context;
	private final InternalServiceConnection serviceConnection;
	private final Messenger incomingMessenger;
	private final Set<String> rangedRegionIds;
	private final Set<String> monitoredRegionIds;
	private Messenger serviceMessenger;
	private RangingListener rangingListener;
	private MonitoringListener monitoringListener;
	private ErrorListener errorListener;
	private ServiceReadyCallback callback;
	private ScanPeriodData foregroundScanPeriod;
	private ScanPeriodData backgroundScanPeriod;

	public BeaconManager(Context context)
	{
		this.context = ((Context)Preconditions.checkNotNull(context));
		this.serviceConnection = new InternalServiceConnection();
		this.incomingMessenger = new Messenger(new IncomingHandler());
		this.rangedRegionIds = new HashSet<String>();
		this.monitoredRegionIds = new HashSet<String>();
	}

	public boolean hasBluetooth()
	{
		return this.context.getPackageManager().hasSystemFeature("android.hardware.bluetooth_le");
	}

	public boolean isBluetoothEnabled()
	{
		if (!checkPermissionsAndService()) {
			L.e("AndroidManifest.xml does not contain android.permission.BLUETOOTH or android.permission.BLUETOOTH_ADMIN permissions. BeaconService may be also not declared in AndroidManifest.xml.");
			return false;
		}

		BluetoothManager bluetoothManager = (BluetoothManager)this.context.getSystemService("bluetooth");
		BluetoothAdapter adapter = bluetoothManager.getAdapter();
		return (adapter != null) && (adapter.isEnabled());
	}

	public boolean checkPermissionsAndService()
	{
		PackageManager pm = this.context.getPackageManager();
		int bluetoothPermission = pm.checkPermission("android.permission.BLUETOOTH", this.context.getPackageName());
		int bluetoothAdminPermission = pm.checkPermission("android.permission.BLUETOOTH_ADMIN", this.context.getPackageName());
 
		Intent intent = new Intent(this.context, BeaconService.class);
		List resolveInfo = pm.queryIntentServices(intent, 65536);
 
		return (bluetoothPermission == 0) && (bluetoothAdminPermission == 0) && (resolveInfo.size() > 0);
   }

	public void connect(ServiceReadyCallback callback)
	{
		if (!checkPermissionsAndService()) {
			L.e("AndroidManifest.xml does not contain android.permission.BLUETOOTH or android.permission.BLUETOOTH_ADMIN permissions. BeaconService may be also not declared in AndroidManifest.xml.");
		}
		this.callback = ((ServiceReadyCallback)Preconditions.checkNotNull(callback, "callback cannot be null"));
		if (isConnectedToService()) {
			callback.onServiceReady();
		}
		
		boolean bound = this.context.bindService(new Intent(this.context, BeaconService.class), this.serviceConnection, 1);
 
		if (!bound)
		L.w("Could not bind service: make sure thatcom.estimote.sdk.service.BeaconService is declared in AndroidManifest.xml");
	}
	

	public void disconnect()
	{
		if (!isConnectedToService()) {
			L.i("Not disconnecting because was not connected to service");
			return;
		}
		
		CopyOnWriteArraySet<String> tempRangedRegionIds = new CopyOnWriteArraySet(this.rangedRegionIds);
		for (String regionId : tempRangedRegionIds) {
			try {
				internalStopRanging(regionId);
			} catch (RemoteException e) {
				L.e("Swallowing error while disconnect/stopRanging", e);
			}
		}

		CopyOnWriteArraySet<String> tempMonitoredRegionIds = new CopyOnWriteArraySet(this.monitoredRegionIds);
		for (String regionId : tempMonitoredRegionIds) {
			try {
				internalStopMonitoring(regionId);
			} catch (RemoteException e) {
				L.e("Swallowing error while disconnect/stopMonitoring", e);
			}
		}

		this.context.unbindService(this.serviceConnection);
		this.serviceMessenger = null;
	}
	

	public void setRangingListener(RangingListener listener)
	{
		this.rangingListener = ((RangingListener)Preconditions.checkNotNull(listener, "listener cannot be null"));
	}

	public void setMonitoringListener(MonitoringListener listener)
	{
		this.monitoringListener = ((MonitoringListener)Preconditions.checkNotNull(listener, "listener cannot be null"));
	}

	public void setErrorListener(ErrorListener listener)
	{
		this.errorListener = listener;
		if ((isConnectedToService()) && (listener != null))
			registerErrorListenerInService();
	}

	public void setForegroundScanPeriod(long scanPeriodMillis, long waitTimeMillis)
	{
		if (isConnectedToService())
			setScanPeriod(new ScanPeriodData(scanPeriodMillis, waitTimeMillis), 10);
		else
			this.foregroundScanPeriod = new ScanPeriodData(scanPeriodMillis, waitTimeMillis);
	}

	public void setBackgroundScanPeriod(long scanPeriodMillis, long waitTimeMillis)
	{
		if (isConnectedToService())
			setScanPeriod(new ScanPeriodData(scanPeriodMillis, waitTimeMillis), 9);
		else
			this.backgroundScanPeriod = new ScanPeriodData(scanPeriodMillis, waitTimeMillis);
	}

	private void setScanPeriod(ScanPeriodData scanPeriodData, int msgId)
	{
		Message scanPeriodMsg = Message.obtain(null, msgId);
		scanPeriodMsg.obj = scanPeriodData;
		try {
			this.serviceMessenger.send(scanPeriodMsg);
		} catch (RemoteException e) {
			L.e("Error while setting scan periods: " + msgId);
		}
	}

	private void registerErrorListenerInService() {
		Message registerMsg = Message.obtain(null, 7);
		registerMsg.replyTo = this.incomingMessenger;
		try {
			this.serviceMessenger.send(registerMsg);
		} catch (RemoteException e) {
			L.e("Error while registering error listener");
		}
	}

	public void startRanging(Region region)
			throws RemoteException
			{
				if (!isConnectedToService()) {
					L.i("Not starting ranging, not connected to service");
					return;
				}
				Preconditions.checkNotNull(region, "region cannot be null");
 
				if (this.rangedRegionIds.contains(region.getIdentifier())) {
					L.i("Region already ranged but that's OK: " + region);
				}
				this.rangedRegionIds.add(region.getIdentifier());
 
				Message startRangingMsg = Message.obtain(null, 1);
				startRangingMsg.obj = region;
				startRangingMsg.replyTo = this.incomingMessenger;
				try {
					this.serviceMessenger.send(startRangingMsg);
				} catch (RemoteException e) {
					L.e("Error while starting ranging", e);
					throw e;
				}
			}
 
	public void stopRanging(Region region) throws RemoteException {
		if (!isConnectedToService()) {
			L.i("Not stopping ranging, not connected to service");
			return;
		}
		
		Preconditions.checkNotNull(region, "region cannot be null");
		internalStopRanging(region.getIdentifier());
	}


	private void internalStopRanging(String regionId) throws RemoteException {
		this.rangedRegionIds.remove(regionId);
		Message stopRangingMsg = Message.obtain(null, 2);
		stopRangingMsg.obj = regionId;
		try {
			this.serviceMessenger.send(stopRangingMsg);
		} catch (RemoteException e) {
			L.e("Error while stopping ranging", e);
			throw e;
		}
	}

	public void startMonitoring(Region region) throws RemoteException {
		if (!isConnectedToService()) {
			L.i("Not starting monitoring, not connected to service");
			return;
		}
		Preconditions.checkNotNull(region, "region cannot be null");
 
		if (this.monitoredRegionIds.contains(region.getIdentifier())) {
			L.i("Region already monitored but that's OK: " + region);
		}

		this.monitoredRegionIds.add(region.getIdentifier());
		
		Message startMonitoringMsg = Message.obtain(null, 4);
		startMonitoringMsg.obj = region;
		startMonitoringMsg.replyTo = this.incomingMessenger;
		try {
			this.serviceMessenger.send(startMonitoringMsg);
		} catch (RemoteException e) {
			L.e("Error while starting monitoring", e);
			throw e;
		}
	}
 
	public void stopMonitoring(Region region) throws RemoteException {
		if (!isConnectedToService()) {
			L.i("Not stopping monitoring, not connected to service");
			return;
		}
		Preconditions.checkNotNull(region, "region cannot be null");
		internalStopMonitoring(region.getIdentifier());
   }


	private void internalStopMonitoring(String regionId) throws RemoteException {
		this.monitoredRegionIds.remove(regionId);
		Message stopMonitoringMsg = Message.obtain(null, 5);
		stopMonitoringMsg.obj = regionId;
		try {
			this.serviceMessenger.send(stopMonitoringMsg);
		} catch (RemoteException e) {
			L.e("Error while stopping ranging");
			throw e;
		}
	}

	private boolean isConnectedToService() {
		return this.serviceMessenger != null;
	}

	private class IncomingHandler extends Handler
	{
		private IncomingHandler()
		{
		
		}
 
		public void handleMessage(Message msg)
		{
			switch (msg.what) {
				case 3:
					if (BeaconManager.this.rangingListener != null) {
						RangingResult rangingResult = (RangingResult)msg.obj;
						BeaconManager.this.rangingListener.onBeaconsDiscovered(rangingResult.region, rangingResult.beacons);
					}break;
				case 6:
					if (BeaconManager.this.monitoringListener != null) {
						MonitoringResult monitoringResult = (MonitoringResult)msg.obj;
						if (monitoringResult.state == Region.State.INSIDE)
							BeaconManager.this.monitoringListener.onEnteredRegion(monitoringResult.region);
						else
							BeaconManager.this.monitoringListener.onExitedRegion(monitoringResult.region);
					}
					break;
				case 8:
					if (BeaconManager.this.errorListener != null) {
						Integer errorId = (Integer)msg.obj;
						BeaconManager.this.errorListener.onError(errorId);
					}break;
				default:
					L.d("Unknown message: " + msg);
					}
				}
			}
 
	private class InternalServiceConnection
		implements ServiceConnection
		{
			private InternalServiceConnection()
			{
			}

			public void onServiceConnected(ComponentName name, IBinder service)
			{
				BeaconManager.this.serviceMessenger = new Messenger(service);
				if (BeaconManager.this.errorListener != null) {
					BeaconManager.this.registerErrorListenerInService();
				}

				if (BeaconManager.this.foregroundScanPeriod != null) {
					BeaconManager.this.setScanPeriod(BeaconManager.this.foregroundScanPeriod, 9);
					BeaconManager.this.foregroundScanPeriod = null;
				}

				if (BeaconManager.this.backgroundScanPeriod != null) {
					BeaconManager.this.setScanPeriod(BeaconManager.this.backgroundScanPeriod, 10);
					BeaconManager.this.backgroundScanPeriod = null;
				}
       
				if (BeaconManager.this.callback != null) {
					BeaconManager.this.callback.onServiceReady();
					BeaconManager.this.callback = null;
				}
			}

			
			public void onServiceDisconnected(ComponentName name)
			{
				L.e("Service disconnected, crashed? " + name);
				BeaconManager.this.serviceMessenger = null;
			}
		}
	

		public static abstract interface ErrorListener
		{
			public abstract void onError(Integer paramInteger);
		}
 
		public static abstract interface MonitoringListener
		{
			public abstract void onEnteredRegion(Region paramRegion);
			public abstract void onExitedRegion(Region paramRegion);
		}
 
		public static abstract interface RangingListener
		{
			public abstract void onBeaconsDiscovered(Region paramRegion, List<Beacon> paramList);
		}
 
		public static abstract interface ServiceReadyCallback
		{
			public abstract void onServiceReady();
		}
    }

