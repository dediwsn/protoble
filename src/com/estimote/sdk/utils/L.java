package com.estimote.sdk.utils;

import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

public class L
{
	private static final String TAG = "EstimoteSDK";
	private static boolean ENABLE_DEBUG_LOGGING = false;
	private static boolean ENABLE_CRASHLYTICS_LOGGING = false;
	private static Method CRASHLYTICS_LOG_METHOD;

	public static void enableDebugLogging(boolean enableDebugLogging)
	{
		ENABLE_DEBUG_LOGGING = enableDebugLogging;
	}

	public static void enableCrashlyticsLogging(boolean enableCrashlytics) {
		if (enableCrashlytics)
			try {
				Class crashlytics = Class.forName("com.crashlytics.android.Crashlytics");
				CRASHLYTICS_LOG_METHOD = crashlytics.getMethod("log", new Class[] { String.class });
				ENABLE_CRASHLYTICS_LOGGING = true;
			}
		    catch (ClassNotFoundException e) {
            }

			catch (NoSuchMethodException e) {
			}
		else
			ENABLE_CRASHLYTICS_LOGGING = false;
	}

	public static void v(String msg)
	{
		if (ENABLE_DEBUG_LOGGING) {
			String logMsg = debugInfo() + msg;
			Log.v("EstimoteSDK", logMsg);
			logCrashlytics(logMsg);
		}
	}

	public static void d(String msg) {
		if (ENABLE_DEBUG_LOGGING) {
			String logMsg = debugInfo() + msg;
			Log.d("EstimoteSDK", logMsg);
			logCrashlytics(logMsg);
		}
	}
 
	public static void i(String msg) {
		String logMsg = debugInfo() + msg;
		Log.i("EstimoteSDK", logMsg);
		logCrashlytics(logMsg);
	}

	public static void w(String msg) {
		String logMsg = debugInfo() + msg;
		Log.w("EstimoteSDK", logMsg);
		logCrashlytics(logMsg);
	}

	public static void e(String msg) {
		String logMsg = debugInfo() + msg;
		Log.e("EstimoteSDK", logMsg);
		logCrashlytics(msg);
	}

	public static void e(String msg, Throwable e) {
		String logMsg = debugInfo() + msg;
		Log.e("EstimoteSDK", logMsg, e);
		logCrashlytics(msg + " " + throwableAsString(e));
	}

	public static void wtf(String msg) {
		String logMsg = debugInfo() + msg;
		Log.wtf("EstimoteSDK", logMsg);
		logCrashlytics(logMsg);
	}

	public static void wtf(String msg, Exception exception) {
		String logMsg = debugInfo() + msg;
		Log.wtf("EstimoteSDK", logMsg, exception);
		logCrashlytics(logMsg + " " + throwableAsString(exception));
	}

	private static String debugInfo()
	{
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		String className = stackTrace[4].getClassName();
		String methodName = Thread.currentThread().getStackTrace()[4].getMethodName();
		int lineNumber = stackTrace[4].getLineNumber();
		return className + "." + methodName + ":" + lineNumber + " ";
	}

	private static void logCrashlytics(String msg)
	{
		if (ENABLE_CRASHLYTICS_LOGGING)
			try {
				CRASHLYTICS_LOG_METHOD.invoke(null, new Object[] { debugInfo() + msg });
			}
			catch (Exception e)
			{
			}
	}

	private static String throwableAsString(Throwable e)
	{
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		return sw.toString();
   }
}

