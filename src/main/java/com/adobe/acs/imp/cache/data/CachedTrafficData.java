package com.adobe.acs.imp.cache.data;

public class CachedTrafficData implements Comparable<CachedTrafficData>{
	private final int date;
	private final int clicks;
	private final int impressions;
	private final int users;
	private final double reach;

	public int compareTo(CachedTrafficData o) {
		if (o == null)
			throw new IllegalArgumentException("Input CachedTrafficData is NULL.");
		return this.getDate() - o.getDate();
	}

	public CachedTrafficData(int date, int clicks, int impressisons, int users, double reach) {
		this.date = date;
		this.clicks = clicks;
		this.impressions = impressisons;
		this.users = users;
		this.reach = reach;
	}
	public int getDate() {
		return date;
	}

	public int getClicks() {
		return clicks;
	}

	public int getImpressions() {
		return impressions;
	}

	public long getUsers() {
		return users;
	}

	public double getReach() {
		return reach;
	}

}
