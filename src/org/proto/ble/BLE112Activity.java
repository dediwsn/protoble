package org.proto.ble;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

public class BLE112Activity extends Activity {

	private static final String TAG = "BLE112Activity";
	public static final String SELECTED_SENSOR = "info.shangma.selected_sensor";

	private BluetoothGatt mConnectedGatt;

	private String sensor;

	private TextView mNumber;
	private String firstDigit;
	private String otherDigits;
	
	private static final int bleSize = 30;
	private XYPlot blePlot = null;
	private SimpleXYSeries bleSeries = null;
	
	private int receivedSize = 0;
	
	private long timeForFirstByte;
	private long timeForLastByte;
	private boolean readyForFirstByte = true;
 
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		//setContentView(R.layout.sensordata);

		sensor = getIntent().getStringExtra(SELECTED_SENSOR);
		//Log.i(TAG, "the sensor is: " + sensor);

		//mNumber = (TextView) findViewById(R.id.text_Number);

		BluetoothDevice device = Utils.mDevices.get(sensor);
		//Log.i(TAG, "Connecting to " + proxymityUUID);

		mConnectedGatt = device.connectGatt(BLE112Activity.this, false,
				mGattCallback);

		
		//blePlot = (XYPlot) findViewById(R.id.blePlot);
		bleSeries = new SimpleXYSeries("BLE");
		bleSeries.useImplicitXVals();
		blePlot.setRangeBoundaries(-1, 120, BoundaryMode.FIXED);
		blePlot.setDomainBoundaries(0, 30, BoundaryMode.FIXED);
		blePlot.addSeries(bleSeries, new LineAndPointFormatter(Color.RED, Color.GREEN, null, null));
		blePlot.setDomainStepValue(5);
		blePlot.setTicksPerRangeLabel(1);
		blePlot.setDomainLabel("Ble");
		
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		if (mConnectedGatt == null) {
			return ;
		}
		mConnectedGatt.close();
		mConnectedGatt = null;
	}

	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
	}

	private void updateDisplayValues(int number) {
		mNumber.setText("it's " + number);
	}
	private void clearDisplayValues() {
		mNumber.setText("---");
	}

	private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		/* State Machine Tracking */
		private int mState = 0;

		private void reset() {
			mState = 0;
		}

		private void advance() {
			mState++;
		}
		
		private void readSensor(BluetoothGatt gatt) {
			BluetoothGattCharacteristic characteristic = gatt.getService(
					Utils.BLESHIELD_SERVICE).getCharacteristic(
					Utils.BLESHIELD_READ);
			gatt.readCharacteristic(characteristic);
		}
		
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {
			// TODO Auto-generated method stub
			Log.i(TAG, "Current state " + connectionState(newState));
			
			if (status == BluetoothGatt.GATT_SUCCESS
					&& newState == BluetoothProfile.STATE_CONNECTED) {
				
				gatt.discoverServices();

			} else if (status == BluetoothGatt.GATT_SUCCESS
					&& newState == BluetoothProfile.STATE_DISCONNECTED) {
				
			} else if (status != BluetoothGatt.GATT_SUCCESS) {
				
				gatt.disconnect();
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			// TODO Auto-generated method stub
			Log.d(TAG, "Services Discovered: " + status);
			readSensor(gatt);
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onCharacteristicRead");
			if (Utils.BLESHIELD_READ.equals(characteristic.getUuid())) {
				int flag = characteristic.getProperties();			
				Log.d(TAG, "property is: " + flag);

				int offset = 0;				
				gatt.setCharacteristicNotification(characteristic, true);
				BluetoothGattDescriptor desc = characteristic
						.getDescriptor(Utils.BLESHIELD_NOTIFYCONFIG);
				if (desc == null) {
					Log.i(TAG, "it's null");
					return;
				}
				desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				gatt.writeDescriptor(desc);
			}
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onCharacteristicWrite");
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			// TODO Auto-generated method stub
			receivedSize += characteristic.getValue().length;
			
			if ((receivedSize > 1) && readyForFirstByte) {
				timeForFirstByte = System.currentTimeMillis();
				readyForFirstByte = false;
			}
			
			if (receivedSize > 700) {
				timeForLastByte = System.currentTimeMillis();
				Log.i(TAG, "time: " + (timeForLastByte - timeForFirstByte));
			}
			
			Log.i(TAG, "bytes received: " + receivedSize);
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onDescriptorWrite");
		}

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onReadRemoteRssi");
		}

		private String connectionState(int status) {
			switch (status) {
			case BluetoothProfile.STATE_CONNECTED:
				return "Connected";
			case BluetoothProfile.STATE_DISCONNECTED:
				return "Disconnected";
			case BluetoothProfile.STATE_CONNECTING:
				return "Connecting";
			case BluetoothProfile.STATE_DISCONNECTING:
				return "Disconnecting";
			default:
				return String.valueOf(status);
			}
		}

	};
	
	private String extractFirstDigit(BluetoothGattCharacteristic characteristic) {
		
		String firstdigit = Converters.getAsciiValue(characteristic.getValue());
		return firstdigit;
	}
	
	private String extractOtherDigits(BluetoothGattCharacteristic characteristic) {
		String otherDigits = Converters.getAsciiValue(characteristic.getValue());
		return otherDigits;
	}
}
