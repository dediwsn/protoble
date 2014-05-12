 package com.estimote.sdk.service;
 
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.Region;
import com.estimote.sdk.Region.State;
import com.estimote.sdk.Utils;
import com.estimote.sdk.internal.Preconditions;
import com.estimote.sdk.utils.EstimoteBeacons;
import com.estimote.sdk.utils.L;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BeaconService extends Service
{
	public static final int MSG_START_RANGING = 1;
	public static final int MSG_STOP_RANGING = 2;
	public static final int MSG_RANGING_RESPONSE = 3;
	public static final int MSG_START_MONITORING = 4;
	public static final int MSG_STOP_MONITORING = 5;
	public static final int MSG_MONITORING_RESPONSE = 6;
	public static final int MSG_REGISTER_ERROR_LISTENER = 7;
	public static final int MSG_ERROR_RESPONSE = 8;
	public static final int MSG_SET_FOREGROUND_SCAN_PERIOD = 9;
	public static final int MSG_SET_BACKGROUND_SCAN_PERIOD = 10;
	public static final int ERROR_COULD_NOT_START_LOW_ENERGY_SCANNING = -1;
	static final long EXPIRATION_MILLIS = TimeUnit.SECONDS.toMillis(10L);
	private static final String SCAN_START_ACTION_NAME = "startScan";
	private static final String AFTER_SCAN_ACTION_NAME = "afterScan";
	private static final Intent SCAN_START_INTENT = new Intent("startScan");
	private static final Intent AFTER_SCAN_INTENT = new Intent("afterScan");
	private final Messenger messenger;
	private final BluetoothAdapter.LeScanCallback leScanCallback;
	private final ConcurrentHashMap<Beacon, Long> beaconsFoundInScanCycle;
	private final List<RangingRegion> rangedRegions;
	private final List<MonitoringRegion> monitoredRegions;
	private BluetoothAdapter adapter;
	private AlarmManager alarmManager;
	private HandlerThread handlerThread;
	private Handler handler;
	private Runnable afterScanCycleTask;
	private boolean scanning;
	private Messenger errorReplyTo;
	private BroadcastReceiver bluetoothBroadcastReceiver;
	private BroadcastReceiver scanStartBroadcastReceiver;
	private PendingIntent scanStartBroadcastPendingIntent;
	private BroadcastReceiver afterScanBroadcastReceiver;
	private PendingIntent afterScanBroadcastPendingIntent;
	private ScanPeriodData foregroundScanPeriod;
	private ScanPeriodData backgroundScanPeriod;

	public BeaconService()
	{
		this.messenger = new Messenger(new IncomingHandler());
		this.leScanCallback = new InternalLeScanCallback();
		this.beaconsFoundInScanCycle = new ConcurrentHashMap();
		this.rangedRegions = new ArrayList();
		this.monitoredRegions = new ArrayList();
		this.foregroundScanPeriod = new ScanPeriodData(TimeUnit.SECONDS.toMillis(1L), TimeUnit.SECONDS.toMillis(0L));
		this.backgroundScanPeriod = new ScanPeriodData(TimeUnit.SECONDS.toMillis(5L), TimeUnit.SECONDS.toMillis(30L));
	}

	public void onCreate()
	{
		super.onCreate();
		L.i("Creating service");
 
		this.alarmManager = ((AlarmManager)getSystemService("alarm"));
		BluetoothManager bluetoothManager = (BluetoothManager)getSystemService("bluetooth");
		this.adapter = bluetoothManager.getAdapter();
		this.afterScanCycleTask = new AfterScanCycleTask();
		this.handlerThread = new HandlerThread("BeaconServiceThread", 10);
		this.handlerThread.start();
		this.handler = new Handler(this.handlerThread.getLooper());

		this.bluetoothBroadcastReceiver = createBluetoothBroadcastReceiver();
		this.scanStartBroadcastReceiver = createScanStartBroadcastReceiver();
		this.afterScanBroadcastReceiver = createAfterScanBroadcastReceiver();
		registerReceiver(this.bluetoothBroadcastReceiver, new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED"));
		registerReceiver(this.scanStartBroadcastReceiver, new IntentFilter("startScan"));
		registerReceiver(this.afterScanBroadcastReceiver, new IntentFilter("afterScan"));
		this.afterScanBroadcastPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, AFTER_SCAN_INTENT, 0);
		this.scanStartBroadcastPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, SCAN_START_INTENT, 0);
	}

	public void onDestroy()
	{
		L.i("Service destroyed");
		unregisterReceiver(this.bluetoothBroadcastReceiver);
		unregisterReceiver(this.scanStartBroadcastReceiver);
		unregisterReceiver(this.afterScanBroadcastReceiver);

		if (this.adapter != null) {
			stopScanning();
		}

		removeAfterScanCycleCallback();
		this.handlerThread.quit();

		super.onDestroy();
	}
 
	public IBinder onBind(Intent intent)
	{
		return this.messenger.getBinder();
	}
 
	private void startRanging(RangingRegion rangingRegion) {
		checkNotOnUiThread();
		L.v("Start ranging: " + rangingRegion.region);
		Preconditions.checkNotNull(this.adapter, "Bluetooth adapter cannot be null");
		this.rangedRegions.add(rangingRegion);
		startScanning();
	}

	private void stopRanging(String regionId) {
		L.v("Stopping ranging: " + regionId);
		checkNotOnUiThread();
		Iterator iterator = this.rangedRegions.iterator();
		while (iterator.hasNext()) {
			RangingRegion rangingRegion = (RangingRegion)iterator.next();
			if (regionId.equals(rangingRegion.region.getIdentifier())) {
				iterator.remove();
			}
		}
     
		if ((this.rangedRegions.isEmpty()) && (this.monitoredRegions.isEmpty())) {
			removeAfterScanCycleCallback();
			stopScanning();
			this.beaconsFoundInScanCycle.clear();
		}
	}

	public void startMonitoring(MonitoringRegion monitoringRegion) {
		checkNotOnUiThread();
		L.v("Starting monitoring: " + monitoringRegion.region);
		Preconditions.checkNotNull(this.adapter, "Bluetooth adapter cannot be null");
		this.monitoredRegions.add(monitoringRegion);
		startScanning();
   }

	public void stopMonitoring(String regionId) {
		L.v("Stopping monitoring: " + regionId);
		checkNotOnUiThread();
		Iterator iterator = this.monitoredRegions.iterator();
		while (iterator.hasNext()) {
			MonitoringRegion monitoringRegion = (MonitoringRegion)iterator.next();
			if (regionId.equals(monitoringRegion.region.getIdentifier())) {
				iterator.remove();
			}
		}
     
		if ((this.monitoredRegions.isEmpty()) && (this.rangedRegions.isEmpty())) {
			removeAfterScanCycleCallback();
			stopScanning();
			this.beaconsFoundInScanCycle.clear();
		}
	}

   
	private void startScanning() {
		if (this.scanning) {
			L.d("Scanning already in progress, not starting one more");
			
			return;
		}
    
		if ((this.monitoredRegions.isEmpty()) && (this.rangedRegions.isEmpty())) {
			L.d("Not starting scanning, no monitored on ranged regions");
      
			return;
		}
    
		if (!this.adapter.isEnabled()) {
			L.d("Bluetooth is disabled, not starting scanning");
      
			return;
		}
     
		if (!this.adapter.startLeScan(this.leScanCallback)) {
			L.wtf("Bluetooth adapter did not start le scan");
			sendError(Integer.valueOf(-1));
      
			return;
		}
		
		this.scanning = true;
		removeAfterScanCycleCallback();
		setAlarm(this.afterScanBroadcastPendingIntent, scanPeriodTimeMillis());
	
	}

	private void stopScanning()
	{
		try {
			this.scanning = false;
			this.adapter.stopLeScan(this.leScanCallback);
		} catch (Exception e) {
			L.wtf("BluetoothAdapter throws unexpected exception", e);
		}
	}
 
	private void sendError(Integer errorId) {
		if (this.errorReplyTo != null) {
			Message errorMsg = Message.obtain(null, 8);
			errorMsg.obj = errorId;
			try {
				
				this.errorReplyTo.send(errorMsg);
			} catch (RemoteException e) {
				L.e("Error while reporting message, funny right?", e);
			}
		}
	}

   
	private long scanPeriodTimeMillis() {
		if (!this.rangedRegions.isEmpty()) {
			return this.foregroundScanPeriod.scanPeriodMillis;
		}
    
		return this.backgroundScanPeriod.scanPeriodMillis;
	}

	private long scanWaitTimeMillis()
	{
		if (!this.rangedRegions.isEmpty()) {
			return this.foregroundScanPeriod.waitTimeMillis;
		}
		
		return this.backgroundScanPeriod.waitTimeMillis;
	}

	private void setAlarm(PendingIntent pendingIntent, long delayMillis)
	{
		this.alarmManager.set(2, SystemClock.elapsedRealtime() + delayMillis, pendingIntent);
	}
 
	private void checkNotOnUiThread()
	{
		Preconditions.checkArgument(Looper.getMainLooper().getThread() != Thread.currentThread(), "This cannot be run on UI thread, starting BLE scan can be expensive");
		Preconditions.checkNotNull(Boolean.valueOf(this.handlerThread.getLooper() == Looper.myLooper()), "It must be executed on service's handlerThread");
	}
 
	private BroadcastReceiver createBluetoothBroadcastReceiver()
	{
		return new BroadcastReceiver()
		{
			public void onReceive(Context context, Intent intent) {
				if ("android.bluetooth.adapter.action.STATE_CHANGED".equals(intent.getAction())) {
					int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", -1);
					if (state == 10)
						BeaconService.this.handler.post(new Runnable()
						{
							public void run() {
								L.i("Bluetooth is OFF: stopping scanning");
								BeaconService.this.removeAfterScanCycleCallback();
								BeaconService.this.stopScanning();
								BeaconService.this.beaconsFoundInScanCycle.clear();
							}
						});
           
					else if (state == 12)
						BeaconService.this.handler.post(new Runnable()
						{
							public void run() {
								if ((!BeaconService.this.monitoredRegions.isEmpty()) || (!BeaconService.this.rangedRegions.isEmpty())) {
									L.i(String.format("Bluetooth is ON: resuming scanning (monitoring: %d ranging:%d)", new Object[] { Integer.valueOf(BeaconService.this.monitoredRegions.size()), Integer.valueOf(BeaconService.this.rangedRegions.size()) }));
									BeaconService.this.startScanning();
								}
							}
						});
				}
			}
		};
	}
 
	private void removeAfterScanCycleCallback()
	{
		this.handler.removeCallbacks(this.afterScanCycleTask);
		this.alarmManager.cancel(this.afterScanBroadcastPendingIntent);
		this.alarmManager.cancel(this.scanStartBroadcastPendingIntent);
	}

	private BroadcastReceiver createAfterScanBroadcastReceiver() {
		return new BroadcastReceiver()
		{
			public void onReceive(Context context, Intent intent) {
				BeaconService.this.handler.post(BeaconService.this.afterScanCycleTask);
			}
		};
	}

	private BroadcastReceiver createScanStartBroadcastReceiver() {
		return new BroadcastReceiver()
		{
			public void onReceive(Context context, Intent intent) {
				BeaconService.this.handler.post(new Runnable()
				{
					public void run() {
						BeaconService.this.startScanning();
					}
				});
			}
		};
	}
 
	private class InternalLeScanCallback implements BluetoothAdapter.LeScanCallback
	{
		private InternalLeScanCallback()
		{
		
		}
 
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord)
		{
			BeaconService.this.checkNotOnUiThread();
			Beacon beacon = Utils.beaconFromLeScan(device, rssi, scanRecord);
			//if ((beacon == null) || (!EstimoteBeacons.isEstimoteBeacon(beacon))) {
			//	L.v("Device " + device + " is not an Estimote beacon");
			//	return;
			//}
			
			BeaconService.this.beaconsFoundInScanCycle.put(beacon, Long.valueOf(System.currentTimeMillis()));
		}
	}
 
	private class IncomingHandler extends Handler
	{
		private IncomingHandler()
		{
		
		}
 
		public void handleMessage(Message msg)
		{
			final int what = msg.what;
			final Object obj = msg.obj;
			final Messenger replyTo = msg.replyTo;
			BeaconService.this.handler.post(new Runnable()
			{
				public void run() {
					switch (what) {
						case 1:
							RangingRegion rangingRegion = new RangingRegion((Region)obj, replyTo);
							BeaconService.this.startRanging(rangingRegion);
							break;
						case 2:
							String rangingRegionId = (String)obj;
							BeaconService.this.stopRanging(rangingRegionId);
							break;
						case 4:
							MonitoringRegion monitoringRegion = new MonitoringRegion((Region)obj, replyTo);
							BeaconService.this.startMonitoring(monitoringRegion);
							break;
						case 5:
							String monitoredRegionId = (String)obj;
							BeaconService.this.stopMonitoring(monitoredRegionId);
							break;
						case 7:
							BeaconService.this.errorReplyTo = replyTo;
							break;
						case 9:
							L.d("Setting foreground scan period: " + BeaconService.this.foregroundScanPeriod);
							BeaconService.this.foregroundScanPeriod = ((ScanPeriodData)obj);
							break;
						case 10:
							L.d("Setting background scan period: " + BeaconService.this.backgroundScanPeriod);
							BeaconService.this.backgroundScanPeriod = ((ScanPeriodData)obj);
							break;
						case 3:
						case 6:
						case 8:
						default:
							L.d("Unknown message: what=" + what + " obj=" + obj);
					}
				}
			});
		}
	}
 
	private class AfterScanCycleTask implements Runnable
	{
		private AfterScanCycleTask()
		{
    
		}

		private void processRanging()
		{
			for (RangingRegion rangedRegion : BeaconService.this.rangedRegions){
				rangedRegion.processFoundBeacons(BeaconService.this.beaconsFoundInScanCycle);
			}
			
		}

		private List<MonitoringRegion> findEnteredRegions(long currentTimeMillis)
		{
			List didEnterRegions = new ArrayList();
			//for (Map.Entry entry : BeaconService.this.beaconsFoundInScanCycle.entrySet()) {
			for (Entry<Beacon, Long> entry : BeaconService.this.beaconsFoundInScanCycle.entrySet()) {
				for (MonitoringRegion monitoringRegion : matchingMonitoredRegions((Beacon)entry.getKey())) {
					monitoringRegion.processFoundBeacons(BeaconService.this.beaconsFoundInScanCycle);
					if (monitoringRegion.markAsSeen(currentTimeMillis)) {
						didEnterRegions.add(monitoringRegion);
					}
				}
			}
       
			return didEnterRegions;
		}
 
		private List<MonitoringRegion> matchingMonitoredRegions(Beacon beacon) {
			List results = new ArrayList();
			for (MonitoringRegion monitoredRegion : BeaconService.this.monitoredRegions) {
				if (Utils.isBeaconInRegion(beacon, monitoredRegion.region)) {
					results.add(monitoredRegion);
				}
			}
       
			return results;
		}
 
		private void removeNotSeenBeacons(long currentTimeMillis) {
			for (RangingRegion rangedRegion : BeaconService.this.rangedRegions) {
				rangedRegion.removeNotSeenBeacons(currentTimeMillis);
			}
       
			for (MonitoringRegion monitoredRegion : BeaconService.this.monitoredRegions){
				monitoredRegion.removeNotSeenBeacons(currentTimeMillis);
			}
		}
 
		private List<MonitoringRegion> findExitedRegions(long currentTimeMillis)
		{	
			List didExitMonitors = new ArrayList();
			for (MonitoringRegion monitoredRegion : BeaconService.this.monitoredRegions) {
				if (monitoredRegion.didJustExit(currentTimeMillis)) {
					didExitMonitors.add(monitoredRegion);
				}
			}
      
			return didExitMonitors;
		}
 
		private void invokeCallbacks(List<MonitoringRegion> enteredMonitors, List<MonitoringRegion> exitedMonitors) {
			for (RangingRegion rangingRegion : BeaconService.this.rangedRegions) {
				try {
					Message rangingResponseMsg = Message.obtain(null, 3);
					rangingResponseMsg.obj = new RangingResult(rangingRegion.region, rangingRegion.getSortedBeacons());
					rangingRegion.replyTo.send(rangingResponseMsg);
				} catch (RemoteException e) {
					L.e("Error while delivering responses", e);
				}
			}
       
			for (MonitoringRegion didEnterMonitor : enteredMonitors) {
				Message monitoringResponseMsg = Message.obtain(null, 6);
				monitoringResponseMsg.obj = new MonitoringResult(didEnterMonitor.region, Region.State.INSIDE);
				try
				{
					didEnterMonitor.replyTo.send(monitoringResponseMsg);
				} catch (RemoteException e) {
					L.e("Error while delivering responses", e);
				}
			}
			
			for (MonitoringRegion didEnterMonitor : exitedMonitors) {
				Message monitoringResponseMsg = Message.obtain(null, 6);
				monitoringResponseMsg.obj = new MonitoringResult(didEnterMonitor.region, Region.State.OUTSIDE);
				try
				{
					didEnterMonitor.replyTo.send(monitoringResponseMsg);
				} catch (RemoteException e) {
					L.e("Error while delivering responses", e);
				}
			}
		}
 		
		public void run()
		{
			BeaconService.this.checkNotOnUiThread();
			long now = System.currentTimeMillis();
			BeaconService.this.stopScanning();
			processRanging();
			List enteredRegions = findEnteredRegions(now);
			List exitedRegions = findExitedRegions(now);
			removeNotSeenBeacons(now);
			BeaconService.this.beaconsFoundInScanCycle.clear();
			invokeCallbacks(enteredRegions, exitedRegions);
			if (BeaconService.this.scanWaitTimeMillis() == 0L)
				BeaconService.this.startScanning();
			else
				BeaconService.this.setAlarm(BeaconService.this.scanStartBroadcastPendingIntent, BeaconService.this.scanWaitTimeMillis());
		}
	}
}

