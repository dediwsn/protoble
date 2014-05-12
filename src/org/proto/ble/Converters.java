package org.proto.ble;

import android.nfc.Tag;
import android.util.Log;

// Converters - converts value between different numeral system
public class Converters {
	private static final String TAG = "Converters";

    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    // Gets value in hexadecimal system
    public static String getHexValue(byte value[]) {
        if (value == null) {
            return "";
        }

        char[] hexChars = new char[value.length * 3];
        int v;
        for (int j = 0; j < value.length; j++) {
            v = value[j] & 0xFF;
            hexChars[j * 3] = hexArray[v >>> 4];
            Log.i(TAG, "0: "+ hexChars[j*3]);
            hexChars[j * 3 + 1] = hexArray[v & 0x0F];
            Log.i(TAG, "1: "+ hexChars[j*3 + 1]);
            hexChars[j * 3 + 2] = ' ';
            Log.i(TAG, "2: "+ hexChars[j*3 + 2]);
        }
        Log.i(TAG, "end for one hex");

        return new String(hexChars);
    }

    // Gets value in hexadecimal system for single byte
    public static String getHexValue(byte b) {
        char[] hexChars = new char[2];
        int v;
        v = b & 0xFF;
        hexChars[0] = hexArray[v >>> 4];
        hexChars[1] = hexArray[v & 0x0F];
        return new String(hexChars);
    }

    // Gets value in ascii system
    public static String getAsciiValue(byte value[]) {
        if (value == null) {
            return "";
        }

        return new String(value);
    }

    // Gets value in decimal system
    public static String getDecimalValue(byte value[]) {
        if (value == null) {
            return "";
        }

        String result = "";
        for (byte b : value) {
            result += ((int) b & 0xff) + " ";
        }
        return result;
    }

    // Gets value in decimal system for single byte
    public static String getDecimalValue(byte b) {
        String result = "";
        result += ((int) b & 0xff);

        return result;
    }
}
