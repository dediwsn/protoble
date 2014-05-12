package com.estimote.sdk.service;
 
import android.os.Messenger;
import com.estimote.sdk.Region;
 
class MonitoringRegion extends RangingRegion
{
	private static final int NOT_SEEN = -1;
	private long lastSeenTimeMillis = -1L;
	private boolean wasInside;

	public MonitoringRegion(Region region, Messenger replyTo)
	{
		super(region, replyTo);
	}
 
	public boolean markAsSeen(long currentTimeMillis)
	{
		this.lastSeenTimeMillis = currentTimeMillis;
		if (!this.wasInside) {
			this.wasInside = true;
			return true;
		}
		return false;
	}
 
	public boolean isInside(long currentTimeMillis)
	{
		return (this.lastSeenTimeMillis != -1L) && (currentTimeMillis - this.lastSeenTimeMillis < BeaconService.EXPIRATION_MILLIS);
	}

	public boolean didJustExit(long currentTimeMillis)
	{
		if ((this.wasInside) && (!isInside(currentTimeMillis))) {
			this.lastSeenTimeMillis = -1L;
			this.wasInside = false;
			return true;
		}
     
		return false;
	}
 }

