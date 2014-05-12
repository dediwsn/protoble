package com.estimote.sdk.service;

import android.os.Messenger;
import com.estimote.sdk.Beacon;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;
import com.estimote.sdk.utils.L;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class RangingRegion
{
	private static final Comparator<Beacon> BEACON_ACCURACY_COMPARATOR = new Comparator<Beacon>()
	{
		public int compare(Beacon lhs, Beacon rhs) {
			return Double.compare(Utils.computeAccuracy(lhs), Utils.computeAccuracy(rhs));
		}
	};

	private final ConcurrentHashMap<Beacon, Long> beacons;
	final Region region;
	final Messenger replyTo;

	RangingRegion(Region region, Messenger replyTo)
	{
		this.region = region;
		this.replyTo = replyTo;
		this.beacons = new ConcurrentHashMap();
	}

	public final Collection<Beacon> getSortedBeacons()
	{
		ArrayList sortedBeacons = new ArrayList(this.beacons.keySet());
		Collections.sort(sortedBeacons, BEACON_ACCURACY_COMPARATOR);
		return sortedBeacons;
	}

	public final void processFoundBeacons(Map<Beacon, Long> beaconsFoundInScanCycle)
	{
		for (Map.Entry entry : beaconsFoundInScanCycle.entrySet())
			if (Utils.isBeaconInRegion((Beacon)entry.getKey(), this.region))
			{
				this.beacons.remove(entry.getKey());
				this.beacons.put((Beacon)entry.getKey(), (Long)entry.getValue());
			}
	}

	public final void removeNotSeenBeacons(long currentTimeMillis)
	{
		Iterator iterator = this.beacons.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry entry = (Map.Entry)iterator.next();
			if (currentTimeMillis - ((Long)entry.getValue()).longValue() > BeaconService.EXPIRATION_MILLIS) {
				L.v("Not seen lately: " + entry.getKey());
				iterator.remove();
			}
		}
	}
}
